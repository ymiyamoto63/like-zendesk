package dev.likezendesk.identity.infra;

import dev.likezendesk.identity.domain.GroupRef;
import dev.likezendesk.identity.domain.Role;
import dev.likezendesk.identity.domain.UserAccount;
import dev.likezendesk.shared.TenantId;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private static final String COLUMNS = "u.tenant_id, u.id, u.email, u.name, u.role, u.active,"
            + " u.email_verified_at IS NOT NULL AS email_verified, u.password_hash";

    private final JdbcClient jdbc;

    UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record NewUser(
            String email,
            String name,
            Role role,
            @Nullable String passwordHash,
            @Nullable Instant emailVerifiedAt) {}

    public Optional<UserAccount> findById(Viewer viewer, UserId id) {
        var visible = UserVisibility.of(viewer, "u");
        return jdbc.sql("SELECT " + COLUMNS + " FROM users u WHERE " + visible.sql() + " AND u.id = :id")
                .params(visible.params())
                .param("id", id.value())
                .query(UserRepository::map)
                .optional();
    }

    /** ログイン時の照合用。{@code email} は正規化済みであること。 */
    public Optional<UserAccount> findByEmail(Viewer.System viewer, String email) {
        var visible = UserVisibility.of(viewer, "u");
        return jdbc.sql("SELECT " + COLUMNS + " FROM users u WHERE " + visible.sql() + " AND u.email = :email")
                .params(visible.params())
                .param("email", email)
                .query(UserRepository::map)
                .optional();
    }

    /** 同じテナントに同じメールアドレスがあれば {@link org.springframework.dao.DuplicateKeyException}。 */
    public UserAccount insert(Viewer.System viewer, NewUser user, Instant now) {
        var id = jdbc.sql("""
                        INSERT INTO users (tenant_id, email, name, role, password_hash, email_verified_at, created_at)
                        VALUES (:tenant, :email, :name, :role, :hash, :verified, :now)
                        RETURNING id
                        """)
                .param("tenant", viewer.tenantId().value())
                .param("email", user.email())
                .param("name", user.name())
                .param("role", user.role().dbValue())
                .param("hash", user.passwordHash())
                .param("verified", user.emailVerifiedAt() == null ? null : Timestamp.from(user.emailVerifiedAt()))
                .param("now", Timestamp.from(now))
                .query(UUID.class)
                .single();
        return new UserAccount(
                viewer.tenantId(),
                new UserId(id),
                user.email(),
                user.name(),
                user.role(),
                true,
                user.emailVerifiedAt() != null,
                user.passwordHash());
    }

    /** 招待を受諾したユーザーの名前とパスワードを設定し、メールアドレスを確認済みにする。 */
    public void completeInvitation(Viewer.System viewer, UserId id, String name, String passwordHash, Instant now) {
        jdbc.sql("""
                        UPDATE users SET name = :name, password_hash = :hash, email_verified_at = :now
                        WHERE tenant_id = :v_tenant AND id = :id
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .param("id", id.value())
                .param("name", name)
                .param("hash", passwordHash)
                .param("now", Timestamp.from(now))
                .update();
    }

    /** 受諾前の招待を出し直すときに、ロールを変える。 */
    public void changeRole(Viewer.Admin viewer, UserId id, Role role) {
        jdbc.sql("UPDATE users SET role = :role WHERE tenant_id = :v_tenant AND id = :id")
                .param("v_tenant", viewer.tenantId().value())
                .param("id", id.value())
                .param("role", role.dbValue())
                .update();
    }

    public void markEmailVerified(Viewer.System viewer, UserId id, Instant now) {
        jdbc.sql("""
                        UPDATE users SET email_verified_at = COALESCE(email_verified_at, :now)
                        WHERE tenant_id = :v_tenant AND id = :id
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .param("id", id.value())
                .param("now", Timestamp.from(now))
                .update();
    }

    public void changePassword(Viewer.System viewer, UserId id, String passwordHash) {
        jdbc.sql("UPDATE users SET password_hash = :hash WHERE tenant_id = :v_tenant AND id = :id")
                .param("v_tenant", viewer.tenantId().value())
                .param("id", id.value())
                .param("hash", passwordHash)
                .update();
    }

    /** 名前かメールアドレスの部分一致で探す。無効なユーザーは除く。名前順で最大 {@code limit} 件。 */
    public List<UserAccount> search(Viewer.Staff viewer, @Nullable Role role, String query, int limit) {
        var visible = UserVisibility.of(viewer, "u");
        return jdbc.sql("SELECT " + COLUMNS + " FROM users u WHERE " + visible.sql()
                        + " AND u.active AND (CAST(:role AS text) IS NULL OR u.role = :role)"
                        + " AND (u.name ILIKE :pattern OR u.email ILIKE :pattern)"
                        + " ORDER BY u.name, u.email LIMIT :limit")
                .params(visible.params())
                .param("role", role == null ? null : role.dbValue())
                .param(
                        "pattern",
                        "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%")
                .param("limit", limit)
                .query(UserRepository::map)
                .list();
    }

    /** {@code id} のユーザーが所属するグループ。ユーザーが閲覧者に見えなければ空。 */
    public List<GroupRef> groupsOf(Viewer viewer, UserId id) {
        var visible = UserVisibility.of(viewer, "u");
        return jdbc.sql("SELECT g.id, g.name FROM users u"
                        + " JOIN group_memberships m ON m.tenant_id = u.tenant_id AND m.user_id = u.id"
                        + " JOIN agent_groups g ON g.tenant_id = m.tenant_id AND g.id = m.group_id"
                        + " WHERE " + visible.sql() + " AND u.id = :id ORDER BY g.name")
                .params(visible.params())
                .param("id", id.value())
                .query((rs, row) -> new GroupRef(rs.getObject("id", UUID.class), rs.getString("name")))
                .list();
    }

    private static UserAccount map(ResultSet rs, int row) throws SQLException {
        return new UserAccount(
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                new UserId(rs.getObject("id", UUID.class)),
                rs.getString("email"),
                rs.getString("name"),
                Role.fromDb(rs.getString("role")),
                rs.getBoolean("active"),
                rs.getBoolean("email_verified"),
                rs.getString("password_hash"));
    }
}
