package dev.likezendesk.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.likezendesk.shared.UserId;
import dev.likezendesk.ticket.domain.TicketStatusRules.Result;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * ステータス遷移の全組み合わせ（現在 × 要求 × 担当者の有無 × 実行者）を、要件 7 の遷移表から作った期待値と照合する。
 */
class TicketStatusRulesTest {

    private static final Actor STAFF = new Actor.Staff(new UserId(UUID.randomUUID()));
    private static final Actor REQUESTER = new Actor.Requester(new UserId(UUID.randomUUID()));
    private static final Actor SYSTEM = new Actor.System();

    /** 要件 7 の図そのまま。エージェントが明示的に要求できる遷移（同じステータスの要求は「変えない」）。 */
    private static final Map<TicketStatus, Set<TicketStatus>> STAFF_EDGES = Map.of(
            TicketStatus.NEW,
                    EnumSet.of(TicketStatus.OPEN, TicketStatus.PENDING, TicketStatus.ON_HOLD, TicketStatus.SOLVED),
            TicketStatus.OPEN, EnumSet.of(TicketStatus.PENDING, TicketStatus.ON_HOLD, TicketStatus.SOLVED),
            TicketStatus.PENDING, EnumSet.of(TicketStatus.OPEN, TicketStatus.ON_HOLD, TicketStatus.SOLVED),
            TicketStatus.ON_HOLD, EnumSet.of(TicketStatus.OPEN, TicketStatus.PENDING, TicketStatus.SOLVED),
            TicketStatus.SOLVED, EnumSet.of(TicketStatus.OPEN),
            TicketStatus.CLOSED, EnumSet.noneOf(TicketStatus.class));

    static Stream<Arguments> allCombinations() {
        var cases = new ArrayList<Arguments>();
        var requests = new ArrayList<@Nullable TicketStatus>(List.of(TicketStatus.values()));
        requests.add(null);
        for (var current : TicketStatus.values()) {
            for (var requested : requests) {
                for (var hasAssignee : List.of(true, false)) {
                    for (var actor : List.of(STAFF, REQUESTER, SYSTEM)) {
                        cases.add(Arguments.of(current, requested, hasAssignee, actor));
                    }
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{3}: {0} -> {1} (assignee: {2})")
    @MethodSource("allCombinations")
    void matchesTheTransitionTable(
            TicketStatus current, @Nullable TicketStatus requested, boolean hasAssignee, Actor actor) {
        var expected = expectedTarget(current, requested, hasAssignee, actor);
        var result = TicketStatusRules.next(current, requested, hasAssignee, actor);
        if (expected == null || !consistent(expected, hasAssignee)) {
            assertThat(result).isInstanceOf(Result.Rejected.class);
        } else {
            assertThat(result).isEqualTo(new Result.Ok(expected));
        }
    }

    /** 遷移表から求めた遷移先。遷移できなければ null。 */
    private static @Nullable TicketStatus expectedTarget(
            TicketStatus current, @Nullable TicketStatus requested, boolean hasAssignee, Actor actor) {
        if (current == TicketStatus.CLOSED) {
            return null;
        }
        return switch (actor) {
            case Actor.Staff _ -> {
                if (requested == null || requested == current) {
                    yield current == TicketStatus.NEW && hasAssignee ? TicketStatus.OPEN : current;
                }
                yield STAFF_EDGES.get(current).contains(requested) ? requested : null;
            }
            case Actor.Requester _ -> {
                if (requested == null) {
                    yield current;
                }
                yield switch (requested) {
                    case OPEN ->
                        Set.of(TicketStatus.PENDING, TicketStatus.SOLVED).contains(current)
                                ? TicketStatus.OPEN
                                : current;
                    case SOLVED -> TicketStatus.SOLVED;
                    default -> null;
                };
            }
            case Actor.System _ -> {
                if (requested == null || requested == current) {
                    yield current;
                }
                yield current == TicketStatus.SOLVED && requested == TicketStatus.CLOSED ? TicketStatus.CLOSED : null;
            }
        };
    }

    /** 担当者がいるなら新規にはなれず、解決済み・クローズには担当者が要る。 */
    private static boolean consistent(TicketStatus status, boolean hasAssignee) {
        return switch (status) {
            case NEW -> !hasAssignee;
            case SOLVED, CLOSED -> hasAssignee;
            default -> true;
        };
    }

    @ParameterizedTest
    @MethodSource("namedRejections")
    void explainsWhy(
            TicketStatus current, @Nullable TicketStatus requested, boolean hasAssignee, Actor actor, String code) {
        assertThat(TicketStatusRules.next(current, requested, hasAssignee, actor))
                .isEqualTo(new Result.Rejected(code));
    }

    static Stream<Arguments> namedRejections() {
        return Stream.of(
                Arguments.of(TicketStatus.CLOSED, null, true, STAFF, "status.closed_is_final"),
                Arguments.of(TicketStatus.OPEN, TicketStatus.NEW, false, STAFF, "status.cannot_return_to_new"),
                Arguments.of(TicketStatus.OPEN, TicketStatus.SOLVED, false, STAFF, "status.solved_requires_assignee"),
                Arguments.of(TicketStatus.SOLVED, TicketStatus.CLOSED, true, STAFF, "status.closed_by_system_only"),
                Arguments.of(TicketStatus.SOLVED, TicketStatus.PENDING, true, STAFF, "status.reopen_before_changing"),
                Arguments.of(
                        TicketStatus.OPEN, TicketStatus.PENDING, true, REQUESTER, "status.not_allowed_for_requester"));
    }
}
