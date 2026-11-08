package dev.likezendesk.tenant.infra;

import dev.likezendesk.shared.Viewer;
import dev.likezendesk.tenant.domain.RequesterSignup;
import dev.likezendesk.tenant.domain.Tenant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 管理者によるテナント設定の変更（F-X-6）。対象は閲覧者のテナントだけ。 */
@Repository
public class TenantSettingsRepository {

    private final JdbcClient jdbc;

    TenantSettingsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Tenant get(Viewer.Admin viewer) {
        return jdbc.sql("SELECT " + TenantDirectory.COLUMNS + " FROM tenants WHERE id = :v_tenant")
                .param("v_tenant", viewer.tenantId().value())
                .query(TenantDirectory::map)
                .single();
    }

    public Tenant update(
            Viewer.Admin viewer, String name, RequesterSignup requesterSignup, List<String> allowedDomains) {
        return jdbc.sql("UPDATE tenants SET name = :name, requester_signup = :signup, allowed_domains = :domains"
                        + " WHERE id = :v_tenant RETURNING " + TenantDirectory.COLUMNS)
                .param("v_tenant", viewer.tenantId().value())
                .param("name", name)
                .param("signup", requesterSignup.dbValue())
                .param("domains", allowedDomains.toArray(String[]::new))
                .query(TenantDirectory::map)
                .single();
    }
}
