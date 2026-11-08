package dev.likezendesk.ticket.domain;

import dev.likezendesk.shared.ApiException;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * チケット一覧の絞り込み条件と並び順（F-V-2、F-V-3）。ビューの定義と同じ形（docs/design/data-model.md 3.6）。
 *
 * <p>{@code field} と {@code op} の検証は {@link Criterion#parse} が行う。SQL への変換は ticket.infra が {@link #criteria()} から行い、値はすべてバインド変数にする。
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

    /** 条件を型付きにする。形が正しくなければ 422（{@code view.invalid_condition}）。SQL への変換はこの結果から行う。 */
    public List<Criterion> criteria() {
        if (all.size() > MAX_CONDITIONS) {
            throw ApiException.rule("view.invalid_condition", "Invalid view condition: too many conditions");
        }
        return all.stream().map(Criterion::parse).toList();
    }

    /** 条件の形と並び順を検証して、値を正規化した絞り込み条件を返す。違反があれば 422。 */
    public TicketFilter validated() {
        var conditions = criteria().stream().map(Criterion::toCondition).toList();
        for (var s : sort) {
            if (!SORT_FIELDS.contains(s.field())) {
                throw ApiException.rule("view.invalid_sort", "Unknown sort field: " + s.field());
            }
        }
        return new TicketFilter(conditions, sort);
    }
}
