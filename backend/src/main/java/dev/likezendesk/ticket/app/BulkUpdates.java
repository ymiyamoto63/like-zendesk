package dev.likezendesk.ticket.app;

import dev.likezendesk.shared.ApiException;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.ticket.domain.Patch;
import dev.likezendesk.ticket.domain.Ticket;
import dev.likezendesk.ticket.domain.TicketStatus;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * 複数チケットの一括更新（F-T-8）。チケットごとに通常の更新（{@link Tickets#update}）を別のトランザクションで行い、
 * 規則に合わないチケットだけを失敗として返す。監査ログと通知も 1 件ずつの更新と同じになる。
 */
@Service
public class BulkUpdates {

    public static final int MAX_TICKETS = 100;

    private final Tickets tickets;

    BulkUpdates(Tickets tickets) {
        this.tickets = tickets;
    }

    /** タグは置き換えずに足し引きする（チケットごとにタグが違うため）。 */
    public record Changes(
            @Nullable TicketStatus status,
            Patch<UUID> groupId,
            Patch<UUID> assigneeId,
            List<String> addTags,
            List<String> removeTags) {}

    public record Failure(long number, String code) {}

    public record Result(List<Ticket> updated, List<Failure> failed) {}

    public Result update(Viewer.Staff viewer, List<Long> numbers, Changes changes) {
        var distinct = new LinkedHashSet<>(numbers);
        if (distinct.isEmpty() || distinct.size() > MAX_TICKETS) {
            throw ApiException.rule("bulk.invalid_count", "Choose 1 to " + MAX_TICKETS + " tickets");
        }
        var add = Ticket.normalizeTags(changes.addTags());
        var remove = Ticket.normalizeTags(changes.removeTags());
        var updated = new ArrayList<Ticket>();
        var failed = new ArrayList<Failure>();
        for (var number : distinct) {
            try {
                var current = tickets.get(viewer, number);
                updated.add(tickets.update(
                        viewer,
                        number,
                        new Tickets.Changes(
                                current.version(),
                                null,
                                changes.status(),
                                null,
                                null,
                                changes.groupId(),
                                changes.assigneeId(),
                                tags(current.tags(), add, remove))));
            } catch (ApiException e) {
                failed.add(new Failure(number, e.code()));
            }
        }
        return new Result(updated, failed);
    }

    private static @Nullable List<String> tags(List<String> current, List<String> add, List<String> remove) {
        if (add.isEmpty() && remove.isEmpty()) {
            return null;
        }
        var next = new LinkedHashSet<>(current);
        next.addAll(add);
        remove.forEach(next::remove);
        return List.copyOf(next);
    }
}
