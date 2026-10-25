package dev.likezendesk.tenant.infra;

import dev.likezendesk.shared.TenantId;
import dev.likezendesk.tenant.domain.RequesterSignup;
import dev.likezendesk.tenant.domain.Tenant;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * テナントの一覧と識別子の解決。閲覧者を取らない唯一のリポジトリ（ArchUnit の規則で名指しで除外している）。
 *
 * <p>テナントの行そのものだけを扱い、テナントに属するデータは扱わない。
 */
@Repository
public class TenantDirectory {

    private final JdbcClient jdbc;

    TenantDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Tenant> findBySlug(String slug) {
        return jdbc.sql("SELECT id, slug, name, requester_signup FROM tenants WHERE slug = :slug")
                .param("slug", slug)
                .query(TenantDirectory::map)
                .optional();
    }

    /** slug が使われていれば {@link org.springframework.dao.DuplicateKeyException}。 */
    public Tenant insert(String slug, String name, RequesterSignup requesterSignup, Instant now) {
        var id = jdbc.sql("""
                        INSERT INTO tenants (slug, name, requester_signup, created_at)
                        VALUES (:slug, :name, :signup, :now)
                        RETURNING id
                        """)
                .param("slug", slug)
                .param("name", name)
                .param("signup", requesterSignup.dbValue())
                .param("now", java.sql.Timestamp.from(now))
                .query(UUID.class)
                .single();
        return new Tenant(new TenantId(id), slug, name, requesterSignup);
    }

    private static Tenant map(ResultSet rs, int row) throws SQLException {
        return new Tenant(
                new TenantId(rs.getObject("id", UUID.class)),
                rs.getString("slug"),
                rs.getString("name"),
                RequesterSignup.fromDb(rs.getString("requester_signup")));
    }
}
