package dev.likezendesk.identity.app;

import dev.likezendesk.identity.domain.EmailAddresses;
import dev.likezendesk.identity.domain.Role;
import dev.likezendesk.identity.domain.UserAccount;
import dev.likezendesk.identity.infra.UserRepository;
import dev.likezendesk.shared.Viewer;
import java.time.Clock;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 招待やメールアドレスの確認を経ずに、すぐログインできるユーザーを作る。デモデータの投入（devtools）だけが使う。
 */
@Service
public class Provisioning {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    Provisioning(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** {@code passwordHash} は {@link #hash} で作った値。ユーザーごとに bcrypt を計算しないよう、呼び出し側で使い回す。 */
    @Transactional
    public UserAccount createUser(
            Viewer.Admin viewer,
            String email,
            String name,
            Role role,
            String passwordHash,
            @Nullable UUID organizationId) {
        var now = clock.instant();
        var user = users.insert(
                new Viewer.System(viewer.tenantId()),
                new UserRepository.NewUser(EmailAddresses.normalize(email), name, role, passwordHash, now),
                now);
        if (organizationId != null) {
            users.setOrganization(viewer, user.id(), organizationId);
        }
        return user;
    }

    public String hash(String password) {
        return passwordEncoder.encode(password);
    }
}
