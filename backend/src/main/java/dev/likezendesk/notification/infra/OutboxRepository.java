package dev.likezendesk.notification.infra;

import dev.likezendesk.notification.domain.NotificationKind;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 通知の Outbox（Transactional Outbox）。業務データの更新と同じトランザクションで書く。 */
@Repository
public class OutboxRepository {

    private static final TypeReference<Map<String, String>> PAYLOAD = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    OutboxRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Pending(UUID id, NotificationKind kind, Map<String, String> payload, int attempts) {}

    public void enqueue(
            Viewer viewer, NotificationKind kind, UserId recipient, Map<String, String> payload, Instant now) {
        jdbc.sql("""
                        INSERT INTO notification_outbox
                          (tenant_id, kind, recipient_id, payload, next_attempt_at, created_at)
                        VALUES (:tenant, :kind, :recipient, CAST(:payload AS jsonb), :now, :now)
                        """)
                .param("tenant", viewer.tenantId().value())
                .param("kind", kind.dbValue())
                .param("recipient", recipient.value())
                .param("payload", json.writeValueAsString(payload))
                .param("now", Timestamp.from(now))
                .update();
    }

    /** 送信時刻の来た行を取り、行ロックをかける。他のプロセスがロック中の行は飛ばす。トランザクションの中で呼ぶこと。 */
    public List<Pending> lockDue(Viewer.System viewer, Instant now, int limit) {
        return jdbc.sql("""
                        SELECT id, kind, payload::text AS payload, attempts FROM notification_outbox
                        WHERE tenant_id = :v_tenant AND state = 'pending' AND next_attempt_at <= :now
                        ORDER BY next_attempt_at
                        LIMIT :limit
                        FOR UPDATE SKIP LOCKED
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .param("now", Timestamp.from(now))
                .param("limit", limit)
                .query((rs, row) -> new Pending(
                        rs.getObject("id", UUID.class),
                        NotificationKind.fromDb(rs.getString("kind")),
                        json.readValue(rs.getString("payload"), PAYLOAD),
                        rs.getInt("attempts")))
                .list();
    }

    /** 送信済みにする。リンク（トークンを含む）は送信後に DB に残さない。 */
    public void markSent(Viewer.System viewer, UUID id, Instant now) {
        jdbc.sql("""
                        UPDATE notification_outbox
                        SET state = 'sent', sent_at = :now, attempts = attempts + 1, payload = payload - 'url'
                        WHERE tenant_id = :v_tenant AND id = :id
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .param("id", id)
                .param("now", Timestamp.from(now))
                .update();
    }

    /** 送信の失敗を記録する。{@code retryAt} が null なら再試行をやめる。 */
    public void markFailed(Viewer.System viewer, UUID id, Instant retryAt) {
        jdbc.sql("""
                        UPDATE notification_outbox
                        SET attempts = attempts + 1,
                            state = CASE WHEN CAST(:retry AS timestamptz) IS NULL THEN 'failed' ELSE 'pending' END,
                            next_attempt_at = COALESCE(CAST(:retry AS timestamptz), next_attempt_at)
                        WHERE tenant_id = :v_tenant AND id = :id
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .param("id", id)
                .param("retry", retryAt == null ? null : Timestamp.from(retryAt))
                .update();
    }
}
