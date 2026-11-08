package dev.likezendesk.identity.app;

import dev.likezendesk.identity.domain.EmailAddresses;
import dev.likezendesk.identity.domain.Role;
import dev.likezendesk.identity.infra.UserRepository;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.tenant.app.Tenants;
import java.time.Clock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** テナントのサインアップ（F-X-1）。テナントと最初の管理者を作る。 */
@Service
public class Signup {

    private final Tenants tenants;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    Signup(Tenants tenants, UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.tenants = tenants;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public record Command(String tenantName, String slug, String adminName, String email, String password) {}

    /** 作成した管理者の閲覧者を返す。呼び出し側はそのままログイン状態にする。 */
    @Transactional
    public Viewer.Admin signup(Command command) {
        var tenant = tenants.create(command.slug(), command.tenantName());
        var now = clock.instant();
        // 最初の管理者はサインアップした本人なので、メールアドレスは確認済みとして扱う
        var admin = users.insert(
                new Viewer.System(tenant.id()),
                new UserRepository.NewUser(
                        EmailAddresses.normalize(command.email()),
                        command.adminName(),
                        Role.ADMIN,
                        passwordEncoder.encode(command.password()),
                        now),
                now);
        return (Viewer.Admin) admin.viewer();
    }
}
