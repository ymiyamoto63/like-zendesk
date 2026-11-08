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

/** 依頼者の自己登録とメールアドレスの確認（F-A-2）。 */
@Service
public class RequesterRegistration {

    private final UserRepository users;
    private final TokenMails tokenMails;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    RequesterRegistration(UserRepository users, TokenMails tokenMails, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.tokenMails = tokenMails;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * 依頼者を未確認の状態で登録し、確認メールを送る。
     *
     * <p>既に登録済みのアドレスでも同じ応答を返し、登録の有無を漏らさない。確認前の依頼者なら確認メールだけを送り直す（パスワードは変えない）。
     */
    @Transactional
    public void register(Tenant tenant, String name, String email, String password) {
        var normalized = EmailAddresses.normalize(email);
        if (!tenant.acceptsRequesterEmail(normalized)) {
            throw ApiException.rule("requester.domain_not_allowed", "This email domain cannot register");
        }
        var system = new Viewer.System(tenant.id());
        var existing = users.findByEmail(system, normalized);
        if (existing.isPresent()) {
            var user = existing.get();
            if (user.role() == Role.REQUESTER && !user.emailVerified()) {
                tokenMails.send(tenant, user.id(), normalized, TokenPurpose.EMAIL_VERIFICATION);
            }
            return;
        }
        var user = users.insert(
                system,
                new UserRepository.NewUser(normalized, name, Role.REQUESTER, passwordEncoder.encode(password), null),
                clock.instant());
        tokenMails.send(tenant, user.id(), normalized, TokenPurpose.EMAIL_VERIFICATION);
    }

    /** メールアドレスを確認済みにし、そのユーザーの閲覧者を返す。 */
    @Transactional
    public Viewer verify(Tenant tenant, String token) {
        var system = new Viewer.System(tenant.id());
        var userId = tokenMails.consume(tenant, TokenPurpose.EMAIL_VERIFICATION, token);
        users.markEmailVerified(system, userId, clock.instant());
        return users.findById(system, userId).orElseThrow().viewer();
    }
}
