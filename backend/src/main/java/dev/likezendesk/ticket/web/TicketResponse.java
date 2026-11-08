package dev.likezendesk.ticket.web;

import dev.likezendesk.ticket.domain.Ref;
import dev.likezendesk.ticket.domain.Ticket;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** チケット詳細（docs/design/api.md 3.1）。 */
record TicketResponse(
        long number,
        String subject,

        @Schema(allowableValues = {"new", "open", "pending", "on_hold", "solved", "closed"})
        String status,

        @Schema(allowableValues = {"low", "normal", "high", "urgent"})
        String priority,

        @Schema(allowableValues = {"question", "incident", "problem", "task"})
        String type,

        Named requester,
        @Nullable Named group,
        @Nullable Named assignee,
        List<String> tags,

        @Schema(
                types = {"integer", "null"},
                format = "int64")
        @Nullable
        Long followUpOf,

        int version,
        Instant createdAt,
        Instant updatedAt,

        @Schema(
                types = {"string", "null"},
                format = "date-time")
        @Nullable
        Instant solvedAt,

        @Schema(
                types = {"string", "null"},
                format = "date-time")
        @Nullable
        Instant closedAt) {

    record Named(UUID id, String name) {
        static @Nullable Named of(@Nullable Ref ref) {
            return ref == null ? null : new Named(ref.id(), ref.name());
        }
    }

    static TicketResponse of(Ticket t) {
        return new TicketResponse(
                t.number(),
                t.subject(),
                t.status().dbValue(),
                t.priority().dbValue(),
                t.type().dbValue(),
                java.util.Objects.requireNonNull(Named.of(t.requester())),
                Named.of(t.group()),
                Named.of(t.assignee()),
                t.tags(),
                t.followUpOf(),
                t.version(),
                t.createdAt(),
                t.updatedAt(),
                t.solvedAt(),
                t.closedAt());
    }
}
