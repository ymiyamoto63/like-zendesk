package dev.likezendesk.identity.app;

import dev.likezendesk.identity.domain.EmailAddresses;
import dev.likezendesk.identity.domain.TokenPurpose;
import dev.likezendesk.identity.infra.UserRepository;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.tenant.domain.Tenant;
import java.time.Clock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResets {

    private final UserRepository users;
    private final TokenMails tokenMails;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    PasswordResets(UserRepository users, TokenMails tokenMails, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.tokenMails = tokenMails;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * 再設定のメールを送る。有効で、パスワードを設定済みのユーザーにだけ送る（招待の受諾前は招待メールを使う）。
     *
     * <p>ユーザーがいなくても同じ応答を返し、登録の有無を漏らさない。
     */
    @Transactional
    public void request(Tenant tenant, String email) {
        var normalized = EmailAddresses.normalize(email);
        users.findByEmail(new Viewer.System(tenant.id()), normalized)
                .filter(user -> user.active() && user.passwordHash() != null)
                .ifPresent(user -> tokenMails.send(tenant, user.id(), normalized, TokenPurpose.PASSWORD_RESET));
    }

    /** 新しいパスワードを設定する。リンクがメールに届いたので、メールアドレスも確認済みにする。 */
    @Transactional
    public void reset(Tenant tenant, String token, String password) {
        var system = new Viewer.System(tenant.id());
        var userId = tokenMails.consume(tenant, TokenPurpose.PASSWORD_RESET, token);
        users.changePassword(system, userId, passwordEncoder.encode(password));
        users.markEmailVerified(system, userId, clock.instant());
    }
}
