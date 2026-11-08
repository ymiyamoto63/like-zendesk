package dev.likezendesk.devtools.infra;

import dev.likezendesk.shared.Viewer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 性能確認用の大量データ（F-D-2）。サービスを通さず SQL でまとめて書く。監査ログと通知は作らない。 */
@Repository
@Profile({"dev", "e2e"})
public class LoadDataRepository {

    private final JdbcClient jdbc;

    LoadDataRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * {@code count} 件のチケットと、それぞれの最初のコメントを書く。作成日時は {@code since} から {@code now} までに散らす。状態は新規・オープン・保留・解決済み・クローズを混ぜる。
     */
    public void insertTickets(
            Viewer.System viewer,
            List<UUID> requesterIds,
            UUID groupId,
            List<UUID> assigneeIds,
            int count,
            Instant since,
            Instant now) {
        var first = jdbc.sql("""
                        UPDATE tenants SET next_ticket_number = next_ticket_number + :count
                        WHERE id = :v_tenant RETURNING next_ticket_number - :count
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .param("count", count)
                .query(Long.class)
                .single();
        jdbc.sql("""
                        WITH g AS (
                          SELECT n,
                                 CAST(:since AS timestamptz)
                                   + (CAST(:now AS timestamptz) - CAST(:since AS timestamptz)) * (n::float8 / :count)
                                   AS created_at,
                                 (ARRAY['new','open','pending','solved','closed','closed','closed'])[1 + n % 7] AS status
                          FROM generate_series(0, :count - 1) AS n
                        )
                        INSERT INTO tickets (tenant_id, number, subject, requester_id, status, priority, type,
                                             group_id, assignee_id, tags, created_at, updated_at, solved_at, closed_at)
                        SELECT :v_tenant, :first + g.n,
                               (ARRAY['VPN に接続できない','メールが届かない','パスワードを忘れた','プリンターで印刷できない',
                                      '画面が表示されない','ライセンスの追加','アクセス権の申請','PC が遅い'])[1 + g.n % 8]
                                 || ' (' || (:first + g.n) || ')',
                               (CAST(:requesters AS uuid[]))[1 + g.n % cardinality(CAST(:requesters AS uuid[]))],
                               g.status,
                               (ARRAY['low','normal','normal','high','urgent'])[1 + g.n % 5],
                               (ARRAY['question','incident','problem','task'])[1 + g.n % 4],
                               CASE WHEN g.status = 'new' THEN NULL ELSE CAST(:group AS uuid) END,
                               CASE WHEN g.status = 'new' THEN NULL
                                    ELSE (CAST(:assignees AS uuid[]))[1 + g.n % cardinality(CAST(:assignees AS uuid[]))] END,
                               CASE WHEN g.n % 3 = 0 THEN ARRAY['vpn'] WHEN g.n % 3 = 1 THEN ARRAY['mail'] ELSE '{}' END,
                               g.created_at,
                               g.created_at + interval '2 hours',
                               CASE WHEN g.status IN ('solved','closed') THEN g.created_at + interval '1 hour' END,
                               CASE WHEN g.status = 'closed' THEN g.created_at + interval '2 hours' END
                        FROM g
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .param("first", first)
                .param("count", count)
                .param("since", Timestamp.from(since))
                .param("now", Timestamp.from(now))
                .param("requesters", requesterIds.toArray(UUID[]::new))
                .param("group", groupId)
                .param("assignees", assigneeIds.toArray(UUID[]::new))
                .update();
        jdbc.sql("""
                        INSERT INTO comments (tenant_id, ticket_id, author_id, body, public, created_at)
                        SELECT t.tenant_id, t.id, t.requester_id,
                               '性能確認用のチケットです。番号 ' || t.number || ' の本文。', true, t.created_at
                        FROM tickets t
                        WHERE t.tenant_id = :v_tenant AND t.number BETWEEN :first AND :first + :count - 1
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .param("first", first)
                .param("count", count)
                .update();
    }

    /** まとめて書いた後に統計を取り直す。古い統計のままだと一覧が順次走査になる。 */
    public void analyze(Viewer.System viewer) {
        jdbc.sql("ANALYZE tickets, comments").update();
    }
}
