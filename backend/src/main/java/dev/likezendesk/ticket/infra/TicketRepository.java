package dev.likezendesk.ticket.infra;

import dev.likezendesk.shared.SqlCondition;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.ticket.domain.Priority;
import dev.likezendesk.ticket.domain.Ref;
import dev.likezendesk.ticket.domain.Ticket;
import dev.likezendesk.ticket.domain.TicketFilter;
import dev.likezendesk.ticket.domain.TicketPage;
import dev.likezendesk.ticket.domain.TicketStatus;
import dev.likezendesk.ticket.domain.TicketType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TicketRepository {

    /** 表示名と元チケットの番号を結合した列。別名 t のチケット表に続けて使う。 */
    private static final String SELECT = """
            SELECT t.id, t.number, t.subject, t.status, t.priority, t.type, t.tags, t.version,
                   t.created_at, t.updated_at, t.solved_at, t.closed_at,
                   t.requester_id, r.name AS requester_name,
                   t.group_id, g.name AS group_name,
                   t.assignee_id, a.name AS assignee_name,
                   f.number AS follow_up_number
            FROM tickets t
            JOIN users r ON r.tenant_id = t.tenant_id AND r.id = t.requester_id
            LEFT JOIN agent_groups g ON g.tenant_id = t.tenant_id AND g.id = t.group_id
            LEFT JOIN users a ON a.tenant_id = t.tenant_id AND a.id = t.assignee_id
            LEFT JOIN tickets f ON f.tenant_id = t.tenant_id AND f.id = t.follow_up_of
            """;

    private final JdbcClient jdbc;

    TicketRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record NewTicket(
            String subject,
            UUID requesterId,
            TicketStatus status,
            Priority priority,
            TicketType type,
            @Nullable UUID groupId,
            @Nullable UUID assigneeId,
            List<String> tags,
            @Nullable UUID followUpOf) {}

    /** テナント内の連番を払い出す。テナントの行をロックするので、同時に作っても番号は重ならない。 */
    public long nextNumber(Viewer viewer) {
        return jdbc.sql("""
                        UPDATE tenants SET next_ticket_number = next_ticket_number + 1
                        WHERE id = :v_tenant RETURNING next_ticket_number - 1
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .query(Long.class)
                .single();
    }

    public UUID insert(Viewer viewer, long number, NewTicket ticket, Instant now) {
        return jdbc.sql("""
                        INSERT INTO tickets (tenant_id, number, subject, requester_id, status, priority, type,
                                             group_id, assignee_id, tags, follow_up_of, created_at, updated_at)
                        VALUES (:v_tenant, :number, :subject, :requester, :status, :priority, :type,
                                :group, :assignee, :tags, :followUpOf, :now, :now)
                        RETURNING id
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .param("number", number)
                .param("subject", ticket.subject())
                .param("requester", ticket.requesterId())
                .param("status", ticket.status().dbValue())
                .param("priority", ticket.priority().dbValue())
                .param("type", ticket.type().dbValue())
                .param("group", ticket.groupId())
                .param("assignee", ticket.assigneeId())
                .param("tags", ticket.tags().toArray(String[]::new))
                .param("followUpOf", ticket.followUpOf())
                .param("now", Timestamp.from(now))
                .query(UUID.class)
                .single();
    }

    public Optional<Ticket> find(Viewer viewer, long number) {
        return find(viewer, number, "");
    }

    /** 更新のために行をロックして読む（SELECT ... FOR UPDATE）。 */
    public Optional<Ticket> findForUpdate(Viewer viewer, long number) {
        return find(viewer, number, " FOR UPDATE OF t");
    }

    /**
     * {@code solvedBefore} より前に解決済みになったチケットをロックして読む（自動クローズ用）。他のプロセスがロック中の行は飛ばす。
     */
    public List<Ticket> lockSolvedBefore(Viewer.System viewer, Instant solvedBefore, int limit) {
        var visible = TicketVisibility.of(viewer, "t");
        return jdbc.sql(SELECT + " WHERE " + visible.sql()
                        + " AND t.status = 'solved' AND t.solved_at <= :before"
                        + " ORDER BY t.solved_at LIMIT :limit FOR UPDATE OF t SKIP LOCKED")
                .params(visible.params())
                .param("before", Timestamp.from(solvedBefore))
                .param("limit", limit)
                .query(TicketRepository::map)
                .list();
    }

    private Optional<Ticket> find(Viewer viewer, long number, String lock) {
        var visible = TicketVisibility.of(viewer, "t");
        return jdbc.sql(SELECT + " WHERE " + visible.sql() + " AND t.number = :number" + lock)
                .params(visible.params())
                .param("number", number)
                .query(TicketRepository::map)
                .optional();
    }

    /** 条件に合うチケットの 1 ページ。{@code query} が空でなければ全文検索も行う。 */
    public TicketPage search(Viewer.Staff viewer, TicketFilter filter, String query, int page, int size, Instant now) {
        var where = where(viewer, filter, query, now);
        var items = jdbc.sql(SELECT + " WHERE " + where.sql() + " ORDER BY " + TicketFilterSql.orderBy(filter.sort())
                        + " LIMIT :limit OFFSET :offset")
                .params(where.params())
                .param("limit", size)
                .param("offset", (long) page * size)
                .query(TicketRepository::map)
                .list();
        return new TicketPage(items, count(viewer, filter, query, now));
    }

    public long count(Viewer.Staff viewer, TicketFilter filter, String query, Instant now) {
        var where = where(viewer, filter, query, now);
        return jdbc.sql("SELECT count(*) FROM tickets t WHERE " + where.sql())
                .params(where.params())
                .query(Long.class)
                .single();
    }

    private static SqlCondition where(Viewer.Staff viewer, TicketFilter filter, String query, Instant now) {
        var where = TicketVisibility.of(viewer, "t").and(TicketFilterSql.where(viewer, filter, now));
        return query.isBlank() ? where : where.and(TicketFilterSql.search(query.strip()));
    }

    /**
     * {@code expectedVersion} の版を {@code ticket} の内容で置き換える。版が一致しなければ（他の人が先に更新した）false。
     */
    public boolean update(Viewer viewer, Ticket ticket, int expectedVersion) {
        var visible = TicketVisibility.of(viewer, "t");
        var updated = jdbc.sql("""
                        UPDATE tickets t SET subject = :subject, status = :status, priority = :priority, type = :type,
                               group_id = :group, assignee_id = :assignee, tags = :tags, version = :version,
                               updated_at = :updatedAt, solved_at = :solvedAt, closed_at = :closedAt
                        WHERE %s AND t.id = :id AND t.version = :expected
                        """.formatted(visible.sql()))
                .params(visible.params())
                .param("id", ticket.id())
                .param("expected", expectedVersion)
                .param("subject", ticket.subject())
                .param("status", ticket.status().dbValue())
                .param("priority", ticket.priority().dbValue())
                .param("type", ticket.type().dbValue())
                .param("group", ticket.group() == null ? null : ticket.group().id())
                .param(
                        "assignee",
                        ticket.assignee() == null ? null : ticket.assignee().id())
                .param("tags", ticket.tags().toArray(String[]::new))
                .param("version", ticket.version())
                .param("updatedAt", Timestamp.from(ticket.updatedAt()))
                .param("solvedAt", timestamp(ticket.solvedAt()))
                .param("closedAt", timestamp(ticket.closedAt()))
                .update();
        return updated == 1;
    }

    private static @Nullable Timestamp timestamp(@Nullable Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static @Nullable Ref ref(ResultSet rs, String prefix) throws SQLException {
        var id = rs.getObject(prefix + "_id", UUID.class);
        return id == null ? null : new Ref(id, rs.getString(prefix + "_name"));
    }

    private static Ticket map(ResultSet rs, int row) throws SQLException {
        return new Ticket(
                rs.getObject("id", UUID.class),
                rs.getLong("number"),
                rs.getString("subject"),
                Objects.requireNonNull(ref(rs, "requester")),
                TicketStatus.fromDb(rs.getString("status")),
                Priority.fromDb(rs.getString("priority")),
                TicketType.fromDb(rs.getString("type")),
                ref(rs, "group"),
                ref(rs, "assignee"),
                List.of((String[]) rs.getArray("tags").getArray()),
                rs.getObject("follow_up_number", Long.class),
                rs.getInt("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                instant(rs, "solved_at"),
                instant(rs, "closed_at"));
    }
}
