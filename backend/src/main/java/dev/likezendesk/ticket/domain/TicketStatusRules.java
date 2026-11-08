package dev.likezendesk.ticket.domain;

import org.jspecify.annotations.Nullable;

/**
 * ステータス遷移の規則（要件 7）。遷移の判定はここだけで行う。
 *
 * <pre>
 * 新規 → オープン / 保留 / 待機 / 解決済み
 * オープン ⇄ 保留 ⇄ 待機
 * オープン / 保留 / 待機 → 解決済み
 * 解決済み → オープン（依頼者の返信、またはエージェントの再オープン）
 * 解決済み → クローズ（システムによる自動クローズだけ）
 * クローズ → 遷移なし
 * </pre>
 */
public final class TicketStatusRules {

    private TicketStatusRules() {}

    public sealed interface Result {
        record Ok(TicketStatus status) implements Result {}

        /** {@code code} は API のエラー応答の {@code code} になる。 */
        record Rejected(String code) implements Result {}
    }

    /**
     * 変更を適用した後のステータス。
     *
     * @param requested 要求されたステータス。null なら指定なし。依頼者の {@link TicketStatus#OPEN} は「返信した」ことを表し、保留と解決済みのときだけオープンに戻す。
     * @param hasAssigneeAfter 変更を適用した後に担当者がいるか
     */
    public static Result next(
            TicketStatus current, @Nullable TicketStatus requested, boolean hasAssigneeAfter, Actor actor) {
        if (current == TicketStatus.CLOSED) {
            return new Result.Rejected("status.closed_is_final");
        }
        var target = switch (actor) {
            case Actor.Staff _ -> staffTarget(current, requested, hasAssigneeAfter);
            case Actor.Requester _ -> requesterTarget(current, requested);
            case Actor.System _ -> systemTarget(current, requested);
        };
        if (target instanceof Result.Ok(var status)) {
            return check(status, hasAssigneeAfter);
        }
        return target;
    }

    private static Result staffTarget(TicketStatus current, @Nullable TicketStatus requested, boolean hasAssignee) {
        if (requested == null || requested == current) {
            // 新規のチケットに担当者が付いたら、自動でオープンにする
            return new Result.Ok(current == TicketStatus.NEW && hasAssignee ? TicketStatus.OPEN : current);
        }
        return switch (requested) {
            case NEW -> new Result.Rejected("status.cannot_return_to_new");
            case CLOSED -> new Result.Rejected("status.closed_by_system_only");
            case OPEN -> new Result.Ok(TicketStatus.OPEN);
            case PENDING, ON_HOLD, SOLVED ->
                current == TicketStatus.SOLVED
                        ? new Result.Rejected("status.reopen_before_changing")
                        : new Result.Ok(requested);
        };
    }

    private static Result requesterTarget(TicketStatus current, @Nullable TicketStatus requested) {
        if (requested == null) {
            return new Result.Ok(current);
        }
        return switch (requested) {
            case OPEN ->
                new Result.Ok(
                        current == TicketStatus.PENDING || current == TicketStatus.SOLVED
                                ? TicketStatus.OPEN
                                : current);
            case SOLVED -> new Result.Ok(TicketStatus.SOLVED);
            case NEW, PENDING, ON_HOLD, CLOSED -> new Result.Rejected("status.not_allowed_for_requester");
        };
    }

    private static Result systemTarget(TicketStatus current, @Nullable TicketStatus requested) {
        if (requested == null || requested == current) {
            return new Result.Ok(current);
        }
        return requested == TicketStatus.CLOSED && current == TicketStatus.SOLVED
                ? new Result.Ok(TicketStatus.CLOSED)
                : new Result.Rejected("status.invalid_transition");
    }

    /** 遷移先によらない、ステータスと担当者の組み合わせの規則。 */
    private static Result check(TicketStatus status, boolean hasAssignee) {
        if (status == TicketStatus.NEW && hasAssignee) {
            return new Result.Rejected("status.cannot_return_to_new");
        }
        if ((status == TicketStatus.SOLVED || status == TicketStatus.CLOSED) && !hasAssignee) {
            return new Result.Rejected("status.solved_requires_assignee");
        }
        return new Result.Ok(status);
    }
}
