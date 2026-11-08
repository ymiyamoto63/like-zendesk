package dev.likezendesk.identity.app;

import dev.likezendesk.identity.domain.TokenPurpose;
import dev.likezendesk.identity.domain.Tokens;
import dev.likezendesk.identity.infra.UserTokenRepository;
import dev.likezendesk.identity.infra.UserTokenRepository.ValidToken;
import dev.likezendesk.notification.app.Notifications;
import dev.likezendesk.notification.domain.NotificationKind;
import dev.likezendesk.shared.ApiException;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.tenant.domain.Tenant;
import java.time.Clock;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** トークンを発行してリンク付きのメールを Outbox に書く処理と、受け取ったトークンを使う処理。 */
@Component
class TokenMails {

    private final UserTokenRepository tokens;
    private final Notifications notifications;
    private final Clock clock;
    private final String baseUrl;

    TokenMails(
            UserTokenRepository tokens,
            Notifications notifications,
            Clock clock,
            @Value("${app.base-url}") String baseUrl) {
        this.tokens = tokens;
        this.notifications = notifications;
        this.clock = clock;
        this.baseUrl = baseUrl;
    }

    /** 呼び出し側のトランザクションの中で呼ぶ。 */
    void send(Tenant tenant, UserId userId, String email, TokenPurpose purpose) {
        var system = new Viewer.System(tenant.id());
        var token = Tokens.generate();
        tokens.insert(
                system, userId, purpose, Tokens.hash(token), clock.instant().plus(purpose.validity()));
        var url = baseUrl + "/t/" + tenant.slug() + path(purpose) + token;
        notifications.enqueue(
                system, kind(purpose), userId, Map.of("to", email, "tenantName", tenant.name(), "url", url));
    }

    /** 有効なトークンを使用済みにして、その持ち主を返す。無効なら 404（{@code token.invalid}）。 */
    UserId consume(Tenant tenant, TokenPurpose purpose, String token) {
        var system = new Viewer.System(tenant.id());
        var now = clock.instant();
        return tokens.findValid(system, purpose, Tokens.hash(token), now)
                .filter(valid -> tokens.markUsed(system, valid.id(), now))
                .map(ValidToken::userId)
                .orElseThrow(TokenMails::invalid);
    }

    /** 使用済みにせずに、有効なトークンの持ち主を返す（招待の内容の表示用）。 */
    UserId peek(Tenant tenant, TokenPurpose purpose, String token) {
        return tokens.findValid(new Viewer.System(tenant.id()), purpose, Tokens.hash(token), clock.instant())
                .map(ValidToken::userId)
                .orElseThrow(TokenMails::invalid);
    }

    static ApiException invalid() {
        return new ApiException(HttpStatus.NOT_FOUND, "token.invalid", "The link is invalid or has expired");
    }

    private static String path(TokenPurpose purpose) {
        return switch (purpose) {
            case INVITATION -> "/invitations/";
            case EMAIL_VERIFICATION -> "/verify-email/";
            case PASSWORD_RESET -> "/password-reset/";
        };
    }

    private static NotificationKind kind(TokenPurpose purpose) {
        return switch (purpose) {
            case INVITATION -> NotificationKind.INVITATION;
            case EMAIL_VERIFICATION -> NotificationKind.EMAIL_VERIFICATION;
            case PASSWORD_RESET -> NotificationKind.PASSWORD_RESET;
        };
    }
}
