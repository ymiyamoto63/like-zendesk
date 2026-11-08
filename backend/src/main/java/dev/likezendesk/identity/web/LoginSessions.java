package dev.likezendesk.identity.web;

import dev.likezendesk.shared.TenantId;
import dev.likezendesk.shared.UserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * セッションに保存するログイン状態。「テナント ID → ユーザー ID」の対応を持ち、ログイン状態をテナント単位にする（F-X-2）。
 *
 * <p>同じブラウザで、2 つのテナントに別のユーザーとしてログインできる。
 */
final class LoginSessions {

    private static final String ATTRIBUTE = "likezendesk.logins";

    private LoginSessions() {}

    static Optional<UserId> userOf(HttpServletRequest request, TenantId tenantId) {
        var session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(logins(session).get(tenantId.value())).map(UserId::new);
    }

    /** ログインの前後でセッション ID を変え、セッション固定攻撃を防ぐ。 */
    static void login(HttpServletRequest request, TenantId tenantId, UserId userId) {
        request.getSession(true);
        request.changeSessionId();
        update(request.getSession(), logins -> logins.put(tenantId.value(), userId.value()));
    }

    static void logout(HttpServletRequest request, TenantId tenantId) {
        var session = request.getSession(false);
        if (session != null) {
            update(session, logins -> logins.remove(tenantId.value()));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, UUID> logins(HttpSession session) {
        var value = (Map<UUID, UUID>) session.getAttribute(ATTRIBUTE);
        return value == null ? Map.of() : value;
    }

    /** Spring Session は setAttribute された属性だけを保存するので、写しを変更して設定し直す。 */
    private static void update(HttpSession session, java.util.function.Consumer<Map<UUID, UUID>> change) {
        var logins = new HashMap<>(logins(session));
        change.accept(logins);
        session.setAttribute(ATTRIBUTE, logins);
    }
}
