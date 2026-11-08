package dev.likezendesk.ticket.infra;

import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.ticket.domain.ChangeSet;
import dev.likezendesk.ticket.domain.Ref;
import dev.likezendesk.ticket.domain.Ticket.FieldChange;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** 監査ログ（F-L-1）。追記のみで、更新・削除のメソッドは作らない（F-L-2）。 */
@Repository
public class TicketEventRepository {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    TicketEventRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** {@code actor} が null ならシステムによる変更。 */
    public void insert(
            Viewer viewer,
            UUID ticketId,
            UUID changeId,
            @Nullable UserId actor,
            List<FieldChange> changes,
            Instant now) {
        for (var change : changes) {
            jdbc.sql("""
                            INSERT INTO ticket_events
                              (tenant_id, ticket_id, change_id, actor_id, field, from_value, to_value, created_at)
                            VALUES (:v_tenant, :ticket, :change, :actor, :field,
                                    CAST(:from AS jsonb), CAST(:to AS jsonb), :now)
                            """)
                    .param("v_tenant", viewer.tenantId().value())
                    .param("ticket", ticketId)
                    .param("change", changeId)
                    .param("actor", actor == null ? null : actor.value())
                    .param("field", change.field())
                    .param("from", toJson(change.from()))
                    .param("to", toJson(change.to()))
                    .param("now", Timestamp.from(now))
                    .update();
        }
    }

    /** 古い順に、1 回の変更（change_id）ごとにまとめる。依頼者には見せない。 */
    public List<ChangeSet> list(Viewer.Staff viewer, UUID ticketId) {
        record Row(UUID changeId, @Nullable Ref actor, Instant createdAt, FieldChange change) {}
        var rows = jdbc.sql("""
                        SELECT e.change_id, e.field, e.from_value, e.to_value, e.created_at,
                               e.actor_id, u.name AS actor_name
                        FROM ticket_events e
                        LEFT JOIN users u ON u.tenant_id = e.tenant_id AND u.id = e.actor_id
                        WHERE e.tenant_id = :v_tenant AND e.ticket_id = :ticket
                        ORDER BY e.created_at, e.id
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .param("ticket", ticketId)
                .query((rs, n) -> {
                    var actorId = rs.getObject("actor_id", UUID.class);
                    return new Row(
                            rs.getObject("change_id", UUID.class),
                            actorId == null ? null : new Ref(actorId, rs.getString("actor_name")),
                            rs.getTimestamp("created_at").toInstant(),
                            new FieldChange(
                                    rs.getString("field"),
                                    fromJson(rs.getString("from_value")),
                                    fromJson(rs.getString("to_value"))));
                })
                .list();
        var sets = new LinkedHashMap<UUID, List<Row>>();
        rows.forEach(row ->
                sets.computeIfAbsent(row.changeId(), k -> new ArrayList<>()).add(row));
        return sets.entrySet().stream()
                .map(e -> {
                    var first = e.getValue().getFirst();
                    return new ChangeSet(
                            e.getKey(),
                            first.actor(),
                            first.createdAt(),
                            e.getValue().stream().map(Row::change).toList());
                })
                .toList();
    }

    private @Nullable String toJson(@Nullable Object value) {
        return value == null ? null : json.writeValueAsString(value);
    }

    private @Nullable Object fromJson(@Nullable String value) {
        return value == null ? null : json.readValue(value, Object.class);
    }
}
