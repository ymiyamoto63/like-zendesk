package dev.likezendesk.identity.infra;

import dev.likezendesk.shared.Viewer;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class LoginFailureRepository {

    private final JdbcClient jdbc;

    LoginFailureRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public int countSince(Viewer.System viewer, String email, Instant since) {
        return jdbc.sql("""
                        SELECT count(*) FROM login_failures
                        WHERE tenant_id = :tenant AND email = :email AND failed_at >= :since
                        """)
                .param("tenant", viewer.tenantId().value())
                .param("email", email)
                .param("since", Timestamp.from(since))
                .query(Integer.class)
                .single();
    }

    public void record(Viewer.System viewer, String email, Instant at) {
        jdbc.sql("INSERT INTO login_failures (tenant_id, email, failed_at) VALUES (:tenant, :email, :at)")
                .param("tenant", viewer.tenantId().value())
                .param("email", email)
                .param("at", Timestamp.from(at))
                .update();
    }

    public void clear(Viewer.System viewer, String email) {
        jdbc.sql("DELETE FROM login_failures WHERE tenant_id = :tenant AND email = :email")
                .param("tenant", viewer.tenantId().value())
                .param("email", email)
                .update();
    }
}
