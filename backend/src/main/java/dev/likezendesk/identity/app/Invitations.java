package dev.likezendesk.identity.app;

import dev.likezendesk.identity.domain.EmailAddresses;
import dev.likezendesk.identity.domain.Role;
import dev.likezendesk.identity.domain.TokenPurpose;
import dev.likezendesk.identity.infra.UserRepository;
import dev.likezendesk.shared.ApiException;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.tenant.domain.Tenant;
import java.time.Clock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** エージェントと管理者の招待（F-A-1）。 */
@Service
public class Invitations {

    private final UserRepository users;
    private final TokenMails tokenMails;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    Invitations(UserRepository users, TokenMails tokenMails, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.tokenMails = tokenMails;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * 招待メールを送る。ユーザーはパスワードのない状態で作り、受諾までログインできない。
     *
     * <p>受諾前の招待がある相手には、ロールを更新して招待を出し直す。既に登録済みのアドレスなら 409。
     */
    @Transactional
    public void invite(Viewer.Admin viewer, Tenant tenant, String email, Role role) {
        if (role == Role.REQUESTER) {
            throw new IllegalArgumentException("Only staff can be invited");
        }
        var system = new Viewer.System(viewer.tenantId());
        var normalized = EmailAddresses.normalize(email);
        var existing = users.findByEmail(system, normalized);
        if (existing.isPresent() && existing.get().passwordHash() != null) {
            throw ApiException.conflict("user.email_taken", "A user with this email already exists");
        }
        var userId = existing.map(user -> {
                    if (user.role() == Role.REQUESTER) {
                        throw ApiException.conflict("user.email_taken", "A user with this email already exists");
                    }
                    users.changeRole(viewer, user.id(), role);
                    return user.id();
                })
                .orElseGet(() -> users.insert(
                                system,
                                new UserRepository.NewUser(
                                        normalized, normalized.substring(0, normalized.indexOf('@')), role, null, null),
                                clock.instant())
                        .id());
        tokenMails.send(tenant, userId, normalized, TokenPurpose.INVITATION);
    }

    /** 招待されたメールアドレス。招待の受諾画面に表示する。 */
    @Transactional(readOnly = true)
    public String invitedEmail(Tenant tenant, String token) {
        var userId = tokenMails.peek(tenant, TokenPurpose.INVITATION, token);
        return users.findById(new Viewer.System(tenant.id()), userId)
                .orElseThrow(TokenMails::invalid)
                .email();
    }

    /** 名前とパスワードを設定して招待を受諾し、そのユーザーの閲覧者を返す。 */
    @Transactional
    public Viewer accept(Tenant tenant, String token, String name, String password) {
        var system = new Viewer.System(tenant.id());
        var userId = tokenMails.consume(tenant, TokenPurpose.INVITATION, token);
        users.completeInvitation(system, userId, name, passwordEncoder.encode(password), clock.instant());
        return users.findById(system, userId).orElseThrow().viewer();
    }
}
