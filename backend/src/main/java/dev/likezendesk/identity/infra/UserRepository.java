package dev.likezendesk.identity.infra;

import dev.likezendesk.identity.domain.AdminUser;
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

    /** ロールを変える（受諾前の招待の出し直し、管理画面でのエージェントと管理者の切り替え）。 */
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

    private static final String ADMIN_STATUS = """
            CASE WHEN NOT u.active THEN 'inactive'
                 WHEN u.password_hash IS NULL THEN 'invited'
                 WHEN u.email_verified_at IS NULL THEN 'unverified'
                 ELSE 'active' END""";

    /** 管理画面のユーザー一覧（F-A-1）。ロール、状態、名前かメールアドレスの部分一致で絞り込む。名前順。 */
    public List<AdminUser> adminList(Viewer.Admin viewer, AdminUser.Query query, int page, int size) {
        var params = adminParams(query);
        params.put("limit", size);
        params.put("offset", (long) page * size);
        return adminSelect(viewer, adminWhere() + " ORDER BY u.name, u.email LIMIT :limit OFFSET :offset", params);
    }

    public Optional<AdminUser> adminFind(Viewer.Admin viewer, UserId id) {
        var params = new java.util.HashMap<String, @Nullable Object>();
        params.put("id", id.value());
        return adminSelect(viewer, " AND u.id = :id", params).stream().findFirst();
    }

    private List<AdminUser> adminSelect(
            Viewer.Admin viewer, String where, java.util.Map<String, @Nullable Object> params) {
        var visible = UserVisibility.of(viewer, "u");
        return jdbc.sql("SELECT " + COLUMNS + ", u.created_at, o.id AS org_id, o.name AS org_name"
                        + " FROM users u LEFT JOIN organizations o"
                        + " ON o.tenant_id = u.tenant_id AND o.id = u.organization_id"
                        + " WHERE " + visible.sql() + where)
                .params(visible.params())
                .params(params)
                .query((rs, row) -> {
                    var orgId = rs.getObject("org_id", UUID.class);
                    return new AdminUser(
                            map(rs, row),
                            orgId == null ? null : new GroupRef(orgId, rs.getString("org_name")),
                            rs.getTimestamp("created_at").toInstant());
                })
                .list();
    }

    public long adminCount(Viewer.Admin viewer, AdminUser.Query query) {
        var visible = UserVisibility.of(viewer, "u");
        return jdbc.sql("SELECT count(*) FROM users u WHERE " + visible.sql() + adminWhere())
                .params(visible.params())
                .params(adminParams(query))
                .query(Long.class)
                .single();
    }

    private static String adminWhere() {
        return " AND (CAST(:role AS text) IS NULL OR u.role = :role)"
                + " AND (CAST(:status AS text) IS NULL OR " + ADMIN_STATUS + " = :status)"
                + " AND (u.name ILIKE :pattern OR u.email ILIKE :pattern)";
    }

    private static java.util.Map<String, @Nullable Object> adminParams(AdminUser.Query query) {
        var params = new java.util.HashMap<String, @Nullable Object>();
        params.put("role", query.role() == null ? null : query.role().dbValue());
        params.put("status", query.status() == null ? null : query.status().dbValue());
        params.put(
                "pattern",
                "%" + query.query().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        return params;
    }

    /** 有効な管理者の数。最後の管理者を降格・無効化しないために使う。 */
    public long activeAdmins(Viewer.Admin viewer) {
        return jdbc.sql("SELECT count(*) FROM users WHERE tenant_id = :v_tenant AND role = 'admin' AND active")
                .param("v_tenant", viewer.tenantId().value())
                .query(Long.class)
                .single();
    }

    public void setActive(Viewer.Admin viewer, UserId id, boolean active) {
        jdbc.sql("UPDATE users SET active = :active WHERE tenant_id = :v_tenant AND id = :id")
                .param("v_tenant", viewer.tenantId().value())
                .param("id", id.value())
                .param("active", active)
                .update();
    }

    /** 組織が別テナントのものなら外部キーで失敗する。 */
    public void setOrganization(Viewer.Admin viewer, UserId id, @Nullable UUID organizationId) {
        jdbc.sql("UPDATE users SET organization_id = :org WHERE tenant_id = :v_tenant AND id = :id")
                .param("v_tenant", viewer.tenantId().value())
                .param("id", id.value())
                .param("org", organizationId)
                .update();
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
