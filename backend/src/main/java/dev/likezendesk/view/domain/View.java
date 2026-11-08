package dev.likezendesk.view.domain;

import dev.likezendesk.ticket.domain.TicketFilter;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * チケットのビュー（F-V-1、F-V-2）。標準ビューはコードで定義し（ID は {@code builtin:...}）、共有ビューと個人ビューは DB に保存する。
 *
 * <p>{@code ownerId} が null なら共有ビュー。
 */
public record View(
        String id,
        String name,
        Kind kind,
        TicketFilter filter,
        @Nullable UUID ownerId) {

    public enum Kind {
        BUILTIN,
        SHARED,
        PERSONAL;

        public String dbValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final String BUILTIN_PREFIX = "builtin:";

    /** 標準ビュー（docs/design/api.md 3.5）。並び順は画面の表示順。 */
    public static final List<View> BUILTINS = List.of(
            builtin(
                    "my-unsolved",
                    "自分の未解決チケット",
                    TicketFilter.of(List.of(
                            new TicketFilter.Condition("assignee", "eq", TicketFilter.ME), TicketFilter.unsolved()))),
            builtin(
                    "unassigned",
                    "未割り当てチケット",
                    new TicketFilter(
                            List.of(new TicketFilter.Condition("assignee", "is_null", null), TicketFilter.unsolved()),
                            List.of(new TicketFilter.Sort("created_at", false)))),
            builtin(
                    "my-groups-unsolved",
                    "自グループの未解決チケット",
                    TicketFilter.of(List.of(
                            new TicketFilter.Condition("group", "in", List.of(TicketFilter.MY_GROUPS)),
                            TicketFilter.unsolved()))),
            builtin(
                    "recently-updated",
                    "最近更新されたチケット",
                    TicketFilter.of(List.of(new TicketFilter.Condition("updated_at", "within_days", 7)))),
            builtin("all", "すべてのチケット", TicketFilter.of(List.of())));

    private static View builtin(String key, String name, TicketFilter filter) {
        return new View(BUILTIN_PREFIX + key, name, Kind.BUILTIN, filter, null);
    }
}
