package dev.likezendesk.ticket.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 監査ログの 1 回分の変更（F-L-1）。{@code actor} が null ならシステムによる変更。 */
public record ChangeSet(UUID changeId, @Nullable Ref actor, Instant createdAt, List<Ticket.FieldChange> changes) {

    public ChangeSet {
        changes = List.copyOf(changes);
    }
}
