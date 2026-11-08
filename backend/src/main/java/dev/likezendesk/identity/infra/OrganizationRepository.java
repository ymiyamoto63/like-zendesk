package dev.likezendesk.identity.infra;

import dev.likezendesk.identity.domain.Organization;
import dev.likezendesk.shared.Viewer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 依頼者の所属組織。スタッフだけが読む。 */
@Repository
public class OrganizationRepository {

    private final JdbcClient jdbc;

    OrganizationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 名前順。所属する依頼者の数つき。 */
    public List<Organization> list(Viewer.Staff viewer) {
        return jdbc.sql("""
                        SELECT o.id, o.name, count(u.id) AS members
                        FROM organizations o
                        LEFT JOIN users u ON u.tenant_id = o.tenant_id AND u.organization_id = o.id
                        WHERE o.tenant_id = :v_tenant
                        GROUP BY o.id, o.name
                        ORDER BY o.name
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .query((rs, row) ->
                        new Organization(rs.getObject("id", UUID.class), rs.getString("name"), rs.getLong("members")))
                .list();
    }

    public Optional<Organization> find(Viewer.Staff viewer, UUID id) {
        return list(viewer).stream().filter(o -> o.id().equals(id)).findFirst();
    }

    /** 同じ名前があれば {@link org.springframework.dao.DuplicateKeyException}。 */
    public UUID insert(Viewer.Admin viewer, String name) {
        return jdbc.sql("INSERT INTO organizations (tenant_id, name) VALUES (:v_tenant, :name) RETURNING id")
                .param("v_tenant", viewer.tenantId().value())
                .param("name", name)
                .query(UUID.class)
                .single();
    }

    /** 同じ名前があれば {@link org.springframework.dao.DuplicateKeyException}。 */
    public boolean rename(Viewer.Admin viewer, UUID id, String name) {
        return jdbc.sql("UPDATE organizations SET name = :name WHERE tenant_id = :v_tenant AND id = :id")
                        .param("v_tenant", viewer.tenantId().value())
                        .param("id", id)
                        .param("name", name)
                        .update()
                == 1;
    }

    /** 所属していた依頼者は「所属なし」になる。 */
    public boolean delete(Viewer.Admin viewer, UUID id) {
        jdbc.sql("UPDATE users SET organization_id = NULL WHERE tenant_id = :v_tenant AND organization_id = :id")
                .param("v_tenant", viewer.tenantId().value())
                .param("id", id)
                .update();
        return jdbc.sql("DELETE FROM organizations WHERE tenant_id = :v_tenant AND id = :id")
                        .param("v_tenant", viewer.tenantId().value())
                        .param("id", id)
                        .update()
                == 1;
    }
}
