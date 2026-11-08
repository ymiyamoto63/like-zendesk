package dev.likezendesk.ticket.domain;

import dev.likezendesk.shared.ApiException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * チケット一覧の絞り込み条件と並び順（F-V-2、F-V-3）。ビューの定義と同じ形（docs/design/data-model.md 3.6）。
 *
 * <p>{@code field} と {@code op} は許可リストで検証する。SQL への変換は ticket.infra が行い、値はすべてバインド変数にする。
 */
public record TicketFilter(List<Condition> all, List<Sort> sort) {

    /** {@code value} は演算子によって、文字列、文字列のリスト、整数、または null。 */
    public record Condition(
            String field, String op, @Nullable Object value) {}

    public record Sort(String field, boolean descending) {}

    public static final List<Sort> DEFAULT_SORT = List.of(new Sort("updated_at", true));

    /** 実行時に閲覧者から決まる値。 */
    public static final String ME = "@me";

    public static final String MY_GROUPS = "@my_groups";

    static final int MAX_CONDITIONS = 20;

    private static final Set<String> UNSOLVED = Set.of("new", "open", "pending", "on_hold");

    private static final Map<String, Set<String>> OPS = Map.of(
            "status", Set.of("in", "not_in"),
            "priority", Set.of("in", "not_in"),
            "type", Set.of("in", "not_in"),
            "assignee", Set.of("eq", "is_null", "is_not_null"),
            "group", Set.of("in", "is_null", "is_not_null"),
            "tags", Set.of("has_any", "has_all"),
            "created_at", Set.of("within_days", "older_than_days"),
            "updated_at", Set.of("within_days", "older_than_days"));

    public static final Set<String> SORT_FIELDS =
            Set.of("number", "subject", "status", "priority", "type", "created_at", "updated_at");

    public TicketFilter {
        all = List.copyOf(all);
        sort = sort.isEmpty() ? DEFAULT_SORT : List.copyOf(sort);
    }

    public static TicketFilter of(List<Condition> all) {
        return new TicketFilter(all, DEFAULT_SORT);
    }

    /** 未解決（新規・オープン・保留・待機）。標準ビューで使う。 */
    public static Condition unsolved() {
        return new Condition(
                "status", "in", List.copyOf(UNSOLVED.stream().sorted().toList()));
    }

    public TicketFilter withSort(List<Sort> newSort) {
        return new TicketFilter(all, newSort);
    }

    /** 条件の形を検証して、値を正規化した条件を返す。違反があれば 422（{@code view.invalid_condition}）。 */
    public TicketFilter validated() {
        if (all.size() > MAX_CONDITIONS) {
            throw invalid("too many conditions");
        }
        var conditions = all.stream().map(TicketFilter::validate).toList();
        for (var s : sort) {
            if (!SORT_FIELDS.contains(s.field())) {
                throw ApiException.rule("view.invalid_sort", "Unknown sort field: " + s.field());
            }
        }
        return new TicketFilter(conditions, sort);
    }

    private static Condition validate(Condition c) {
        var ops = OPS.get(c.field());
        if (ops == null || !ops.contains(c.op())) {
            throw invalid(c.field() + " " + c.op());
        }
        Object value = switch (c.op()) {
            case "is_null", "is_not_null" -> null;
            case "eq" -> userRef(c.value());
            case "within_days", "older_than_days" -> days(c.value());
            case "in", "not_in" ->
                switch (c.field()) {
                    case "status" ->
                        enumList(c.value(), v -> TicketStatus.fromDb(v).dbValue());
                    case "priority" ->
                        enumList(c.value(), v -> Priority.fromDb(v).dbValue());
                    case "type" -> enumList(c.value(), v -> TicketType.fromDb(v).dbValue());
                    default -> enumList(c.value(), TicketFilter::groupRef);
                };
            default -> tags(c.value());
        };
        return new Condition(c.field(), c.op(), value);
    }

    private static String userRef(@Nullable Object value) {
        if (ME.equals(value)) {
            return ME;
        }
        return uuid(value);
    }

    private static String groupRef(String value) {
        return MY_GROUPS.equals(value) ? MY_GROUPS : uuid(value);
    }

    private static String uuid(@Nullable Object value) {
        try {
            return UUID.fromString(String.valueOf(value)).toString();
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

    private static List<String> enumList(@Nullable Object value, java.util.function.Function<String, String> parse) {
        try {
            return strings(value).stream().map(parse).distinct().toList();
        } catch (IllegalArgumentException e) {
            throw invalid("unknown value");
        }
    }

    private static ApiException invalid(String detail) {
        return ApiException.rule("view.invalid_condition", "Invalid view condition: " + detail);
    }
}
