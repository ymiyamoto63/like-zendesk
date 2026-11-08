package dev.likezendesk.ticket.infra;

import dev.likezendesk.shared.SqlCondition;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.ticket.domain.Criterion;
import dev.likezendesk.ticket.domain.TicketFilter;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 検証済みの {@link TicketFilter} を、別名 t のチケット表に対する SQL にする。列名と演算子は固定の対応表から選び、値はすべてバインド変数にする。
 */
final class TicketFilterSql {

    private static final Map<String, String> COLUMNS = Map.of(
            "status", "t.status",
            "priority", "t.priority",
            "type", "t.type",
            "created_at", "t.created_at",
            "updated_at", "t.updated_at",
            "number", "t.number",
            "subject", "t.subject");

    /** 表示上の順序で並べる列。文字列の辞書順にしない。 */
    private static final Map<String, String> ORDERED = Map.of(
            "status",
            "array_position(ARRAY['new','open','pending','on_hold','solved','closed'], t.status)",
            "priority",
            "array_position(ARRAY['low','normal','high','urgent'], t.priority)",
            "type",
            "array_position(ARRAY['question','incident','problem','task'], t.type)");

    private static final String REQUESTER_ORG = "(SELECT ru.organization_id FROM users ru"
            + " WHERE ru.tenant_id = t.tenant_id AND ru.id = t.requester_id)";

    private TicketFilterSql() {}

    static SqlCondition where(Viewer.Staff viewer, TicketFilter filter, Instant now) {
        var sql = new ArrayList<String>();
        var params = new HashMap<String, Object>();
        params.put("v_tenant", viewer.tenantId().value());
        var criteria = filter.criteria();
        for (int i = 0; i < criteria.size(); i++) {
            sql.add(translate(viewer, criteria.get(i), "f" + i, now, params));
        }
        return new SqlCondition(sql.isEmpty() ? "TRUE" : String.join(" AND ", sql), params);
    }

    private static String translate(
            Viewer.Staff viewer, Criterion criterion, String p, Instant now, Map<String, Object> params) {
        return switch (criterion) {
            case Criterion.EnumIn e -> {
                params.put(p, e.values().toArray(String[]::new));
                var any = COLUMNS.get(e.field().key()) + " = ANY (:" + p + ")";
                yield e.negated() ? "NOT (" + any + ")" : any;
            }
            case Criterion.AssigneeIsMe _ -> {
                params.put(p, viewer.userId().value());
                yield "t.assignee_id = :" + p;
            }
            case Criterion.AssigneeIs a -> {
                params.put(p, a.id());
                yield "t.assignee_id = :" + p;
            }
            case Criterion.AssigneePresence a -> a.present() ? "t.assignee_id IS NOT NULL" : "t.assignee_id IS NULL";
            case Criterion.GroupPresence g -> g.present() ? "t.group_id IS NOT NULL" : "t.group_id IS NULL";
            case Criterion.GroupIn g -> groupIn(viewer, g, p, params);
            case Criterion.OrganizationIn o -> {
                params.put(p, o.ids().toArray(UUID[]::new));
                yield REQUESTER_ORG + " = ANY (:" + p + ")";
            }
            case Criterion.OrganizationPresence o -> REQUESTER_ORG + (o.present() ? " IS NOT NULL" : " IS NULL");
            case Criterion.Tags t -> {
                params.put(p, t.tags().toArray(String[]::new));
                yield "t.tags " + (t.all() ? "@>" : "&&") + " CAST(:" + p + " AS text[])";
            }
            case Criterion.Age a -> {
                params.put(p, Timestamp.from(now.minus(Duration.ofDays(a.days()))));
                yield COLUMNS.get(a.field().key()) + (a.within() ? " >= :" : " < :") + p;
            }
        };
    }

    private static String groupIn(Viewer.Staff viewer, Criterion.GroupIn group, String p, Map<String, Object> params) {
        var parts = new ArrayList<String>();
        if (!group.ids().isEmpty()) {
            params.put(p, group.ids().toArray(UUID[]::new));
            parts.add("t.group_id = ANY (:" + p + ")");
        }
        if (group.includeMine()) {
            params.put("v_user", viewer.userId().value());
            parts.add("t.group_id IN (SELECT m.group_id FROM group_memberships m"
                    + " WHERE m.tenant_id = :v_tenant AND m.user_id = :v_user)");
        }
        return "(" + String.join(" OR ", parts) + ")";
    }

    /** 件名、または本文・コメントの部分一致（F-V-4）。pg_bigm の GIN インデックスが LIKE '%語%' に効く。 */
    static SqlCondition search(String query) {
        var pattern = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return new SqlCondition(
                "(t.subject LIKE :q OR EXISTS (SELECT 1 FROM comments c"
                        + " WHERE c.tenant_id = t.tenant_id AND c.ticket_id = t.id AND c.body LIKE :q))",
                Map.of("q", pattern));
    }

    static String orderBy(List<TicketFilter.Sort> sort) {
        var keys = sort.stream()
                .map(s -> ORDERED.getOrDefault(s.field(), COLUMNS.get(s.field())) + (s.descending() ? " DESC" : " ASC"))
                .collect(Collectors.toCollection(ArrayList::new));
        // 同じ値の行の順序を決め、ページをまたいで重複・欠落しないようにする
        keys.add("t.number DESC");
        return String.join(", ", keys);
    }
}
