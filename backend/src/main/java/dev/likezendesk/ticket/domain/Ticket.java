package dev.likezendesk.ticket.domain;

import dev.likezendesk.shared.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** チケット（F-T-2）。本文は最初のコメントとして別に持つ。 */
public record Ticket(
        UUID id,
        long number,
        String subject,
        Ref requester,
        TicketStatus status,
        Priority priority,
        TicketType type,
        @Nullable Ref group,
        @Nullable Ref assignee,
        List<String> tags,
        @Nullable Long followUpOf,
        int version,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant solvedAt,
        @Nullable Instant closedAt) {

    public Ticket {
        tags = List.copyOf(tags);
    }

    /** 項目の変更要求。null の項目は変更しない。 */
    public record Update(
            @Nullable String subject,
            @Nullable TicketStatus status,
            @Nullable Priority priority,
            @Nullable TicketType type,
            Patch<Ref> group,
            Patch<Ref> assignee,
            @Nullable List<String> tags) {

        public static Update statusOnly(@Nullable TicketStatus status) {
            return new Update(null, status, null, null, Patch.keep(), Patch.keep(), null);
        }
    }

    /** 監査ログの 1 項目。値は JSON にできる型（文字列、リスト、マップ）か null。 */
    public record FieldChange(
            String field, @Nullable Object from, @Nullable Object to) {}

    public record Applied(Ticket ticket, List<FieldChange> changes) {}

    /**
     * 変更を適用した結果と、変わった項目。何も変わらなければ {@code changes} は空。
     *
     * <p>遷移規則に反するときは 422（{@link TicketStatusRules} の理由コード）。担当者がグループに属するかは、ここでは判定しない（呼び出し側が確かめる）。
     */
    public Applied apply(Update update, Actor actor, Instant now) {
        var newGroup = update.group().applyTo(group);
        var newAssignee = update.assignee().applyTo(assignee);
        if (newAssignee != null && newGroup == null) {
            throw ApiException.rule("assignee.requires_group", "An assignee needs a group");
        }
        var newStatus = switch (TicketStatusRules.next(status, update.status(), newAssignee != null, actor)) {
            case TicketStatusRules.Result.Ok(var s) -> s;
            case TicketStatusRules.Result.Rejected(var code) -> throw ApiException.rule(code, "Status change rejected");
        };
        var newSubject = update.subject() == null ? subject : update.subject();
        var newPriority = update.priority() == null ? priority : update.priority();
        var newType = update.type() == null ? type : update.type();
        var newTags = update.tags() == null ? tags : normalizeTags(update.tags());

        var changes = new ArrayList<FieldChange>();
        diff(changes, "subject", subject, newSubject);
        diff(changes, "status", status.dbValue(), newStatus.dbValue());
        diff(changes, "priority", priority.dbValue(), newPriority.dbValue());
        diff(changes, "type", type.dbValue(), newType.dbValue());
        diffRef(changes, "group", group, newGroup);
        diffRef(changes, "assignee", assignee, newAssignee);
        diff(changes, "tags", tags, newTags);
        if (changes.isEmpty()) {
            return new Applied(this, List.of());
        }
        var newSolvedAt = switch (newStatus) {
            case SOLVED, CLOSED -> solvedAt == null ? now : solvedAt;
            default -> null;
        };
        var newClosedAt = newStatus == TicketStatus.CLOSED ? (closedAt == null ? now : closedAt) : null;
        var after = new Ticket(
                id,
                number,
                newSubject,
                requester,
                newStatus,
                newPriority,
                newType,
                newGroup,
                newAssignee,
                newTags,
                followUpOf,
                version + 1,
                createdAt,
                now,
                newSolvedAt,
                newClosedAt);
        return new Applied(after, List.copyOf(changes));
    }

    /** タグは小文字にそろえ、空白を除き、重複を除いて並べ替える。 */
    public static List<String> normalizeTags(List<String> tags) {
        return tags.stream()
                .map(t -> t.strip().toLowerCase(java.util.Locale.ROOT))
                .filter(t -> !t.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }

    private static void diff(List<FieldChange> changes, String field, Object from, Object to) {
        if (!Objects.equals(from, to)) {
            changes.add(new FieldChange(field, from, to));
        }
    }

    private static void diffRef(List<FieldChange> changes, String field, @Nullable Ref from, @Nullable Ref to) {
        var fromId = from == null ? null : from.id();
        var toId = to == null ? null : to.id();
        if (!Objects.equals(fromId, toId)) {
            changes.add(new FieldChange(field, refValue(from), refValue(to)));
        }
    }

    public static @Nullable Map<String, Object> refValue(@Nullable Ref ref) {
        return ref == null ? null : Map.of("id", ref.id().toString(), "name", ref.name());
    }
}
