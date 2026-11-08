package dev.likezendesk.ticket.infra;

import dev.likezendesk.shared.SqlCondition;
import dev.likezendesk.shared.Viewer;
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
        for (int i = 0; i < filter.all().size(); i++) {
            var c = filter.all().get(i);
            var p = "f" + i;
            sql.add(
                    switch (c.field() + " " + c.op()) {
                        case "status in", "priority in", "type in" -> {
                            params.put(p, strings(c.value()));
                            yield COLUMNS.get(c.field()) + " = ANY (:" + p + ")";
                        }
                        case "status not_in", "priority not_in", "type not_in" -> {
                            params.put(p, strings(c.value()));
                            yield "NOT (" + COLUMNS.get(c.field()) + " = ANY (:" + p + "))";
                        }
                        case "assignee eq" -> {
                            params.put(
                                    p,
                                    TicketFilter.ME.equals(c.value())
                                            ? viewer.userId().value()
                                            : UUID.fromString((String) c.value()));
                            yield "t.assignee_id = :" + p;
                        }
                        case "assignee is_null" -> "t.assignee_id IS NULL";
                        case "assignee is_not_null" -> "t.assignee_id IS NOT NULL";
                        case "group is_null" -> "t.group_id IS NULL";
                        case "group is_not_null" -> "t.group_id IS NOT NULL";
                        case "group in" -> groupIn(viewer, (List<?>) c.value(), p, params);
                        // 依頼者の所属組織（F-A-4）
                        case "organization in" -> {
                            params.put(
                                    p,
                                    ((List<?>) c.value())
                                            .stream()
                                                    .map(v -> UUID.fromString((String) v))
                                                    .toArray(UUID[]::new));
                            yield REQUESTER_ORG + " = ANY (:" + p + ")";
                        }
                        case "organization is_null" -> REQUESTER_ORG + " IS NULL";
                        case "organization is_not_null" -> REQUESTER_ORG + " IS NOT NULL";
                        case "tags has_any" -> {
                            params.put(p, strings(c.value()));
                            yield "t.tags && CAST(:" + p + " AS text[])";
                        }
                        case "tags has_all" -> {
                            params.put(p, strings(c.value()));
                            yield "t.tags @> CAST(:" + p + " AS text[])";
                        }
                        case "created_at within_days", "updated_at within_days" -> {
                            params.put(p, daysAgo(now, c.value()));
                            yield COLUMNS.get(c.field()) + " >= :" + p;
                        }
                        case "created_at older_than_days", "updated_at older_than_days" -> {
                            params.put(p, daysAgo(now, c.value()));
                            yield COLUMNS.get(c.field()) + " < :" + p;
                        }
                        default -> throw new IllegalArgumentException("Unvalidated condition: " + c);
                    });
        }
        return new SqlCondition(sql.isEmpty() ? "TRUE" : String.join(" AND ", sql), params);
    }

    private static String groupIn(Viewer.Staff viewer, List<?> values, String p, Map<String, Object> params) {
        var parts = new ArrayList<String>();
        var ids = values.stream()
                .filter(v -> !TicketFilter.MY_GROUPS.equals(v))
                .map(v -> UUID.fromString((String) v))
                .toArray(UUID[]::new);
        if (ids.length > 0) {
            params.put(p, ids);
            parts.add("t.group_id = ANY (:" + p + ")");
        }
        if (values.contains(TicketFilter.MY_GROUPS)) {
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

    private static String[] strings(Object value) {
        return ((List<?>) value).stream().map(String::valueOf).toArray(String[]::new);
    }

    private static Timestamp daysAgo(Instant now, Object days) {
        return Timestamp.from(now.minus(Duration.ofDays(((Number) days).longValue())));
    }
}
