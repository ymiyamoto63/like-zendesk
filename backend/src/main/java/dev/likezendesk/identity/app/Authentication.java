package dev.likezendesk.identity.app;

import dev.likezendesk.identity.domain.EmailAddresses;
import dev.likezendesk.identity.domain.UserAccount;
import dev.likezendesk.identity.infra.LoginFailureRepository;
import dev.likezendesk.identity.infra.UserRepository;
import dev.likezendesk.shared.ApiException;
import dev.likezendesk.shared.TenantId;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * ログインの照合と、セッションに保存したユーザーから閲覧者を作る処理。
 *
 * <p>照合はトランザクションを張らずに行う。失敗の記録を、例外で巻き戻さないため。
 */
@Service
public class Authentication {

    static final int MAX_FAILURES = 10;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);

    private final UserRepository users;
    private final LoginFailureRepository failures;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String dummyHash;

    Authentication(
            UserRepository users, LoginFailureRepository failures, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.failures = failures;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /**
     * メールアドレスとパスワードを照合する。
     *
     * <p>ユーザーがいない、無効、パスワード未設定、パスワード違いを区別せず、同じ 401 を返す。ユーザーがいなくてもハッシュの照合を行い、応答時間からも区別させない。
     */
    public Viewer login(TenantId tenantId, String email, String password) {
        var system = new Viewer.System(tenantId);
        var normalized = EmailAddresses.normalize(email);
        var now = clock.instant();
        if (failures.countSince(system, normalized, now.minus(FAILURE_WINDOW)) >= MAX_FAILURES) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS, "login.too_many_attempts", "Too many failed login attempts");
        }
        var user = users.findByEmail(system, normalized);
        var hash = user.map(UserAccount::passwordHash).orElse(null);
        var matches = passwordEncoder.matches(password, hash == null ? dummyHash : hash);
        if (!matches || hash == null || !user.get().active()) {
            failures.record(system, normalized, now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "login.invalid_credentials", "Invalid email or password");
        }
        failures.clear(system, normalized);
        return user.get().viewer();
    }

    /** セッションに保存したユーザーの閲覧者。ユーザーが無効化されていれば空。 */
    public Optional<Viewer> viewerOf(TenantId tenantId, UserId userId) {
        return users.findById(new Viewer.System(tenantId), userId)
                .filter(UserAccount::active)
                .map(UserAccount::viewer);
    }
}
