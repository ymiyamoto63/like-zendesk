package dev.likezendesk.ticket.web;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.ticket.app.Tickets;
import dev.likezendesk.ticket.domain.ChangeSet;
import dev.likezendesk.ticket.domain.Comment;
import dev.likezendesk.ticket.domain.Patch;
import dev.likezendesk.ticket.domain.Priority;
import dev.likezendesk.ticket.domain.TicketFilter;
import dev.likezendesk.ticket.domain.TicketStatus;
import dev.likezendesk.ticket.domain.TicketType;
import dev.likezendesk.view.app.Views;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** スタッフ向けのチケット API（docs/design/api.md 2.2）。 */
@RestController
class TicketController {

    private static final String STATUS = "new|open|pending|on_hold|solved|closed";
    private static final String PRIORITY = "low|normal|high|urgent";
    private static final String TYPE = "question|incident|problem|task";
    private static final int MAX_TAGS = 20;

    private final Tickets tickets;
    private final Views views;

    TicketController(Tickets tickets, Views views) {
        this.tickets = tickets;
        this.views = views;
    }

    record CreateTicketRequest(
            @NotNull UUID requesterId,
            @NotBlank @Size(max = 250) String subject,
            @NotBlank @Size(max = 65536) String body,

            @Schema(
                    allowableValues = {"low", "normal", "high", "urgent"},
                    defaultValue = "normal")
            @Pattern(regexp = PRIORITY)
            @Nullable
            String priority,

            @Schema(
                    allowableValues = {"question", "incident", "problem", "task"},
                    defaultValue = "question")
            @Pattern(regexp = TYPE)
            @Nullable
            String type,

            @Nullable UUID groupId,
            @Nullable UUID assigneeId,
            @Size(max = MAX_TAGS) @Nullable List<@NotBlank @Size(max = 50) String> tags,

            @Schema(description = "POST /attachments で受け取った添付 ID（10 個まで）") @Size(max = 10) @Nullable
            List<@NotNull UUID> attachmentIds) {}

    /**
     * 送った項目だけを変える（api.md 3.2）。{@code groupId} と {@code assigneeId} は、null を送ると値を外し、省略すると変えない。
     *
     * <p>省略と null を区別するため、record ではなくクラスにし、セッターが呼ばれたか（JSON に項目があったか）を覚える。
     */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static final class UpdateTicketRequest {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        private @Nullable Integer version;

        @Size(min = 1, max = 250)
        private @Nullable String subject;

        @Schema(allowableValues = {"new", "open", "pending", "on_hold", "solved", "closed"})
        @Pattern(regexp = STATUS)
        private @Nullable String status;

        @Schema(allowableValues = {"low", "normal", "high", "urgent"})
        @Pattern(regexp = PRIORITY)
        private @Nullable String priority;

        @Schema(allowableValues = {"question", "incident", "problem", "task"})
        @Pattern(regexp = TYPE)
        private @Nullable String type;

        @Schema(
                types = {"string", "null"},
                format = "uuid",
                description = "null で担当グループを外す。省略すると変えない")
        private @Nullable UUID groupId;

        @JsonIgnore
        private boolean groupIdSent;

        @Schema(
                types = {"string", "null"},
                format = "uuid",
                description = "null で担当者を外す。省略すると変えない")
        private @Nullable UUID assigneeId;

        @JsonIgnore
        private boolean assigneeIdSent;

        @Size(max = MAX_TAGS)
        private @Nullable List<@NotBlank @Size(max = 50) String> tags;

        public void setVersion(@Nullable Integer version) {
            this.version = version;
        }

        public void setSubject(@Nullable String subject) {
            this.subject = subject;
        }

        public void setStatus(@Nullable String status) {
            this.status = status;
        }

        public void setPriority(@Nullable String priority) {
            this.priority = priority;
        }

        public void setType(@Nullable String type) {
            this.type = type;
        }

        public void setGroupId(@Nullable UUID groupId) {
            this.groupId = groupId;
            this.groupIdSent = true;
        }

        public void setAssigneeId(@Nullable UUID assigneeId) {
            this.assigneeId = assigneeId;
            this.assigneeIdSent = true;
        }

        public void setTags(@Nullable List<String> tags) {
            this.tags = tags;
        }

        Tickets.Changes toChanges() {
            return new Tickets.Changes(
                    java.util.Objects.requireNonNull(version),
                    subject,
                    status == null ? null : TicketStatus.fromDb(status),
                    priority == null ? null : Priority.fromDb(priority),
                    type == null ? null : TicketType.fromDb(type),
                    groupIdSent ? Patch.set(groupId) : Patch.keep(),
                    assigneeIdSent ? Patch.set(assigneeId) : Patch.keep(),
                    tags);
        }
    }

    record TicketListResponse(List<TicketResponse> items, long total, int page, int size) {}

    /**
     * ビューの条件でチケットを探す（F-V-1〜4）。{@code q} があれば件名・本文・コメントを部分一致で探す。
     *
     * <p>{@code sort} は項目名をカンマで区切り、先頭の {@code -} で降順にする（例: {@code -priority,updatedAt}）。省略するとビューの並び順。
     */
    @GetMapping("/api/t/{slug}/tickets")
    TicketListResponse list(
            Viewer.Staff viewer,
            @RequestParam(defaultValue = "builtin:all") @Size(max = 100) String view,
            @RequestParam(defaultValue = "") @Size(max = 200) String q,
            @RequestParam(defaultValue = "") @Pattern(regexp = "^$|^-?[a-zA-Z]+(,-?[a-zA-Z]+){0,2}$") String sort,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        var result = views.tickets(viewer, view, q.strip(), parseSort(sort), page, size);
        return new TicketListResponse(
                result.items().stream().map(TicketResponse::of).toList(), result.total(), page, size);
    }

    /** {@code -updatedAt} を {@code updated_at} の降順にする。項目名の検証は {@link TicketFilter#validated()} が行う。 */
    private static List<TicketFilter.Sort> parseSort(String sort) {
        if (sort.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(sort.split(","))
                .map(part -> {
                    var descending = part.startsWith("-");
                    var name = descending ? part.substring(1) : part;
                    return new TicketFilter.Sort(
                            name.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT), descending);
                })
                .toList();
    }

    @PostMapping("/api/t/{slug}/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    TicketResponse create(Viewer.Staff viewer, @Valid @RequestBody CreateTicketRequest body) {
        return TicketResponse.of(tickets.create(
                viewer,
                new Tickets.NewTicket(
                        body.requesterId(),
                        body.subject(),
                        body.body(),
                        body.priority() == null ? Priority.NORMAL : Priority.fromDb(body.priority()),
                        body.type() == null ? TicketType.QUESTION : TicketType.fromDb(body.type()),
                        body.groupId(),
                        body.assigneeId(),
                        body.tags() == null ? List.of() : body.tags(),
                        ids(body.attachmentIds()))));
    }

    @GetMapping("/api/t/{slug}/tickets/{number}")
    TicketResponse get(Viewer.Staff viewer, @PathVariable long number) {
        return TicketResponse.of(tickets.get(viewer, number));
    }

    @PatchMapping("/api/t/{slug}/tickets/{number}")
    TicketResponse update(
            Viewer.Staff viewer, @PathVariable long number, @Valid @RequestBody UpdateTicketRequest body) {
        return TicketResponse.of(tickets.update(viewer, number, body.toChanges()));
    }

    record CommentResponse(
            UUID id,
            TicketResponse.Named author,
            String body,
            @JsonProperty("public") boolean isPublic,
            Instant createdAt,
            List<AttachmentResponse> attachments) {
        static CommentResponse of(Comment c) {
            return new CommentResponse(
                    c.id(),
                    new TicketResponse.Named(c.author().id(), c.author().name()),
                    c.body(),
                    c.isPublic(),
                    c.createdAt(),
                    AttachmentResponse.of(c.attachments()));
        }
    }

    @GetMapping("/api/t/{slug}/tickets/{number}/comments")
    List<CommentResponse> comments(Viewer.Staff viewer, @PathVariable long number) {
        return tickets.comments(viewer, number).stream()
                .map(CommentResponse::of)
                .toList();
    }

    record AddCommentRequest(
            @NotBlank @Size(max = 65536) String body,
            @JsonProperty("public") @NotNull Boolean isPublic,
            @Valid @Nullable UpdateTicketRequest ticket,

            @Schema(description = "POST /attachments で受け取った添付 ID（10 個まで）") @Size(max = 10) @Nullable
            List<@NotNull UUID> attachmentIds) {}

    record CommentAdded(CommentResponse comment, TicketResponse ticket) {}

    /** コメントを追加する。{@code ticket} を付けると、項目も同じ変更として更新する（api.md 3.3）。 */
    @PostMapping("/api/t/{slug}/tickets/{number}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    CommentAdded addComment(
            Viewer.Staff viewer, @PathVariable long number, @Valid @RequestBody AddCommentRequest body) {
        var result = tickets.comment(
                viewer,
                number,
                body.body(),
                body.isPublic(),
                ids(body.attachmentIds()),
                body.ticket() == null ? null : body.ticket().toChanges());
        return new CommentAdded(CommentResponse.of(result.comment()), TicketResponse.of(result.ticket()));
    }

    static List<UUID> ids(@Nullable List<UUID> attachmentIds) {
        return attachmentIds == null ? List.of() : attachmentIds;
    }

    record EventsResponse(List<ChangeSetResponse> items) {}

    record ChangeSetResponse(
            UUID changeId,
            TicketResponse.@Nullable Named actor,
            Instant createdAt,
            List<FieldChangeResponse> changes) {}

    record FieldChangeResponse(
            String field,

            @Schema(description = "変更前の値。文字列、文字列の配列、{id, name} など項目による") @Nullable
            Object from,

            @Schema(description = "変更後の値") @Nullable Object to) {}

    @GetMapping("/api/t/{slug}/tickets/{number}/events")
    EventsResponse events(Viewer.Staff viewer, @PathVariable long number) {
        return new EventsResponse(tickets.events(viewer, number).stream()
                .map(TicketController::changeSet)
                .toList());
    }

    private static ChangeSetResponse changeSet(ChangeSet set) {
        return new ChangeSetResponse(
                set.changeId(),
                TicketResponse.Named.of(set.actor()),
                set.createdAt(),
                set.changes().stream()
                        .map(c -> new FieldChangeResponse(c.field(), c.from(), c.to()))
                        .toList());
    }
}
