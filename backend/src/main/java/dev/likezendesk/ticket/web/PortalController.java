package dev.likezendesk.ticket.web;

import dev.likezendesk.shared.Viewer;
import dev.likezendesk.ticket.app.Requests;
import dev.likezendesk.ticket.domain.Comment;
import dev.likezendesk.ticket.domain.Ticket;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 依頼者ポータル（docs/design/api.md 2.4）。エージェント用とは別の DTO を返し、担当グループ・タグ・監査ログ・社内メモなどの内部項目を最初から含めない。
 */
@RestController
class PortalController {

    private final Requests requests;

    PortalController(Requests requests) {
        this.requests = requests;
    }

    record PortalTicketResponse(
            long number,
            String subject,

            @Schema(allowableValues = {"new", "open", "pending", "on_hold", "solved", "closed"})
            String status,

            @Schema(description = "担当者の表示名。未割り当てなら null") @Nullable
            String assigneeName,

            @Schema(description = "クローズ後の返信から起票したとき、元のチケットの番号") @Nullable
            Long followUpOf,

            Instant createdAt,
            Instant updatedAt) {

        static PortalTicketResponse of(Ticket t) {
            return new PortalTicketResponse(
                    t.number(),
                    t.subject(),
                    t.status().dbValue(),
                    t.assignee() == null ? null : t.assignee().name(),
                    t.followUpOf(),
                    t.createdAt(),
                    t.updatedAt());
        }
    }

    record PortalTicketListResponse(List<PortalTicketResponse> items, long total, int page, int size) {}

    record PortalCommentResponse(
            UUID id,
            String authorName,
            @Schema(description = "閲覧している依頼者自身が書いたか") boolean mine,
            String body,
            Instant createdAt,
            List<AttachmentResponse> attachments) {

        static PortalCommentResponse of(Comment c, Viewer.Requester viewer) {
            return new PortalCommentResponse(
                    c.id(),
                    c.author().name(),
                    c.author().id().equals(viewer.userId().value()),
                    c.body(),
                    c.createdAt(),
                    AttachmentResponse.of(c.attachments()));
        }
    }

    record PortalCommentsResponse(List<PortalCommentResponse> items) {}

    record NewRequestRequest(
            @NotBlank @Size(max = 250) String subject,
            @NotBlank @Size(max = 65536) String body,

            @Schema(description = "POST /attachments で受け取った添付 ID（10 個まで）") @Size(max = 10) @Nullable
            List<@NotNull UUID> attachmentIds) {}

    record ReplyRequest(
            @NotBlank @Size(max = 65536) String body,

            @Schema(description = "POST /attachments で受け取った添付 ID（10 個まで）") @Size(max = 10) @Nullable
            List<@NotNull UUID> attachmentIds) {}

    record ReplyResponse(
            PortalCommentResponse comment,

            @Schema(description = "返信後のチケット。followUp が true なら新しく起票したチケット")
            PortalTicketResponse ticket,

            @Schema(description = "クローズ済みのチケットへの返信で、新しいチケットを起票したか")
            boolean followUp) {}

    @GetMapping("/api/t/{slug}/portal/tickets")
    PortalTicketListResponse list(
            Viewer.Requester viewer,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        var result = requests.list(viewer, page, size);
        return new PortalTicketListResponse(
                result.items().stream().map(PortalTicketResponse::of).toList(), result.total(), page, size);
    }

    @PostMapping("/api/t/{slug}/portal/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    PortalTicketResponse create(Viewer.Requester viewer, @Valid @RequestBody NewRequestRequest body) {
        return PortalTicketResponse.of(
                requests.create(viewer, body.subject(), body.body(), TicketController.ids(body.attachmentIds())));
    }

    @GetMapping("/api/t/{slug}/portal/tickets/{number}")
    PortalTicketResponse get(Viewer.Requester viewer, @PathVariable long number) {
        return PortalTicketResponse.of(requests.get(viewer, number));
    }

    @GetMapping("/api/t/{slug}/portal/tickets/{number}/comments")
    PortalCommentsResponse comments(Viewer.Requester viewer, @PathVariable long number) {
        return new PortalCommentsResponse(requests.comments(viewer, number).stream()
                .map(c -> PortalCommentResponse.of(c, viewer))
                .toList());
    }

    @PostMapping("/api/t/{slug}/portal/tickets/{number}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    ReplyResponse reply(Viewer.Requester viewer, @PathVariable long number, @Valid @RequestBody ReplyRequest body) {
        var replied = requests.reply(viewer, number, body.body(), TicketController.ids(body.attachmentIds()));
        return new ReplyResponse(
                PortalCommentResponse.of(replied.comment(), viewer),
                PortalTicketResponse.of(replied.ticket()),
                replied.followUp());
    }

    @PostMapping("/api/t/{slug}/portal/tickets/{number}/solve")
    PortalTicketResponse solve(Viewer.Requester viewer, @PathVariable long number) {
        return PortalTicketResponse.of(requests.solve(viewer, number));
    }
}
