package dev.likezendesk.ticket.domain;

import dev.likezendesk.ticket.domain.TicketFilter.Condition;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * 検証済みの絞り込み条件 1 件。{@link Condition}（保存・通信用の文字列表現）を {@link #parse} で型付きの値にする。
 *
 * <p>値の取り違えは {@code parse} で 422 になり、SQL への変換（ticket.infra）は型で場合分けするので、キャストも「検証済みであること」の約束も要らない。
 * 項目や演算子を足すときは、ここに型を足すと SQL 側の switch がコンパイルエラーで知らせる。
 */
public sealed interface Criterion {

    /** 値がステータス・優先度・種別のいずれかの一覧になる項目。 */
    enum EnumField {
        STATUS,
        PRIORITY,
        TYPE;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    enum TimeField {
        CREATED_AT,
        UPDATED_AT;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** {@code status|priority|type} の {@code in} / {@code not_in}。値は正規化済みの DB 値。 */
    record EnumIn(EnumField field, List<String> values, boolean negated) implements Criterion {}

    /** {@code assignee eq @me}。閲覧者本人。 */
    record AssigneeIsMe() implements Criterion {}

    record AssigneeIs(UUID id) implements Criterion {}

    /** {@code assignee is_null|is_not_null}。 */
    record AssigneePresence(boolean present) implements Criterion {}

    /** {@code group in}。{@code includeMine} は {@code @my_groups}（閲覧者の所属グループ）。 */
    record GroupIn(List<UUID> ids, boolean includeMine) implements Criterion {}

    record GroupPresence(boolean present) implements Criterion {}

    /** 依頼者の所属組織（F-A-4）。 */
    record OrganizationIn(List<UUID> ids) implements Criterion {}

    record OrganizationPresence(boolean present) implements Criterion {}

    /** {@code tags has_any|has_all}。タグは正規化済み。 */
    record Tags(List<String> tags, boolean all) implements Criterion {}

    /** {@code created_at|updated_at} の {@code within_days} / {@code older_than_days}。 */
    record Age(TimeField field, int days, boolean within) implements Criterion {}

    /** 保存・通信用の表現に戻す。{@code parse(c.toCondition())} は {@code c} に戻る。 */
    default Condition toCondition() {
        return switch (this) {
            case EnumIn e -> new Condition(e.field().key(), e.negated() ? "not_in" : "in", e.values());
            case AssigneeIsMe _ -> new Condition("assignee", "eq", TicketFilter.ME);
            case AssigneeIs a -> new Condition("assignee", "eq", a.id().toString());
            case AssigneePresence p -> new Condition("assignee", presence(p.present()), null);
            case GroupIn g -> {
                var values = new ArrayList<String>(
                        g.ids().stream().map(UUID::toString).toList());
                if (g.includeMine()) {
                    values.add(TicketFilter.MY_GROUPS);
                }
                yield new Condition("group", "in", values);
            }
            case GroupPresence p -> new Condition("group", presence(p.present()), null);
            case OrganizationIn o ->
                new Condition(
                        "organization",
                        "in",
                        o.ids().stream().map(UUID::toString).toList());
            case OrganizationPresence p -> new Condition("organization", presence(p.present()), null);
            case Tags t -> new Condition("tags", t.all() ? "has_all" : "has_any", t.tags());
            case Age a -> new Condition(a.field().key(), a.within() ? "within_days" : "older_than_days", a.days());
        };
    }

    private static String presence(boolean present) {
        return present ? "is_not_null" : "is_null";
    }

    /** 形が正しくなければ 422（{@code view.invalid_condition}）。 */
    static Criterion parse(Condition c) {
        var field = c.field();
        var op = c.op();
        var value = c.value();
        return switch (field) {
            case "status" ->
                enumIn(EnumField.STATUS, op, value, v -> TicketStatus.fromDb(v).dbValue());
            case "priority" ->
                enumIn(EnumField.PRIORITY, op, value, v -> Priority.fromDb(v).dbValue());
            case "type" ->
                enumIn(EnumField.TYPE, op, value, v -> TicketType.fromDb(v).dbValue());
            case "assignee" ->
                switch (op) {
                    case "eq" -> TicketFilter.ME.equals(value) ? new AssigneeIsMe() : new AssigneeIs(uuid(value));
                    case "is_null" -> new AssigneePresence(false);
                    case "is_not_null" -> new AssigneePresence(true);
                    default -> throw unsupported(c);
                };
            case "group" ->
                switch (op) {
                    case "in" -> groupIn(value);
                    case "is_null" -> new GroupPresence(false);
                    case "is_not_null" -> new GroupPresence(true);
                    default -> throw unsupported(c);
                };
            case "organization" ->
                switch (op) {
                    case "in" -> new OrganizationIn(parseList(value, Criterion::uuid));
                    case "is_null" -> new OrganizationPresence(false);
                    case "is_not_null" -> new OrganizationPresence(true);
                    default -> throw unsupported(c);
                };
            case "tags" ->
                switch (op) {
                    case "has_any" -> new Tags(tags(value), false);
                    case "has_all" -> new Tags(tags(value), true);
                    default -> throw unsupported(c);
                };
            case "created_at" -> age(TimeField.CREATED_AT, c);
            case "updated_at" -> age(TimeField.UPDATED_AT, c);
            default -> throw unsupported(c);
        };
    }

    private static Criterion enumIn(
            EnumField field, String op, @Nullable Object value, Function<String, String> parse) {
        var negated = switch (op) {
            case "in" -> false;
            case "not_in" -> true;
            default -> throw invalid(field.key() + " " + op);
        };
        return new EnumIn(field, parseList(value, parse), negated);
    }

    private static Criterion groupIn(@Nullable Object value) {
        var refs = parseList(value, v -> v);
        var ids = new ArrayList<UUID>();
        var mine = false;
        for (var ref : refs) {
            if (TicketFilter.MY_GROUPS.equals(ref)) {
                mine = true;
            } else {
                ids.add(uuid(ref));
            }
        }
        return new GroupIn(List.copyOf(ids), mine);
    }

    private static Criterion age(TimeField field, Condition c) {
        var within = switch (c.op()) {
            case "within_days" -> true;
            case "older_than_days" -> false;
            default -> throw unsupported(c);
        };
        return new Age(field, days(c.value()), within);
    }

    private static <T> List<T> parseList(@Nullable Object value, Function<String, T> parse) {
        try {
            return strings(value).stream().map(parse).distinct().toList();
        } catch (IllegalArgumentException e) {
            throw invalid("unknown value");
        }
    }

    private static UUID uuid(@Nullable Object value) {
        if (!(value instanceof String s)) {
            throw invalid("not an id: " + value);
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw invalid("not an id: " + value);
        }
    }

    private static int days(@Nullable Object value) {
        if (value instanceof Number n && n.intValue() >= 1 && n.intValue() <= 3650 && n.doubleValue() == n.intValue()) {
            return n.intValue();
        }
        throw invalid("days must be 1..3650");
    }

    private static List<String> tags(@Nullable Object value) {
        var tags = Ticket.normalizeTags(strings(value));
        if (tags.isEmpty()) {
            throw invalid("tags must not be blank");
        }
        return tags;
    }

    private static List<String> strings(@Nullable Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty() || list.size() > 50) {
            throw invalid("value must be a non-empty list");
        }
        return list.stream()
                .map(v -> {
                    if (!(v instanceof String s)) {
                        throw invalid("value must be a list of strings");
                    }
                    return s;
                })
                .toList();
    }

    private static dev.likezendesk.shared.ApiException unsupported(Condition c) {
        return invalid(c.field() + " " + c.op());
    }

    private static dev.likezendesk.shared.ApiException invalid(String detail) {
        return dev.likezendesk.shared.ApiException.rule("view.invalid_condition", "Invalid view condition: " + detail);
    }
}
