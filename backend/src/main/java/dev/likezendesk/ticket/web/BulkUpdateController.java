package dev.likezendesk.ticket.web;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.ticket.app.BulkUpdates;
import dev.likezendesk.ticket.domain.Patch;
import dev.likezendesk.ticket.domain.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 複数チケットの一括更新（F-T-8、api.md 2.2）。 */
@RestController
class BulkUpdateController {

    private final BulkUpdates bulk;

    BulkUpdateController(BulkUpdates bulk) {
        this.bulk = bulk;
    }

    /** 送った項目だけを変える。{@code groupId} と {@code assigneeId} は、null を送ると外し、省略すると変えない。 */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static final class BulkUpdateRequest {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "対象のチケット番号（100 件まで）")
        @NotEmpty
        @Size(max = BulkUpdates.MAX_TICKETS)
        private @Nullable List<@NotNull Long> numbers;

        @Schema(allowableValues = {"new", "open", "pending", "on_hold", "solved", "closed"})
        @Pattern(regexp = "new|open|pending|on_hold|solved|closed")
        private @Nullable String status;

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

        @Size(max = 20)
        private @Nullable List<@NotBlank @Size(max = 50) String> addTags;

        @Size(max = 20)
        private @Nullable List<@NotBlank @Size(max = 50) String> removeTags;

        public void setNumbers(@Nullable List<Long> numbers) {
            this.numbers = numbers;
        }

        public void setStatus(@Nullable String status) {
            this.status = status;
        }

        public void setGroupId(@Nullable UUID groupId) {
            this.groupId = groupId;
            this.groupIdSent = true;
        }

        public void setAssigneeId(@Nullable UUID assigneeId) {
            this.assigneeId = assigneeId;
            this.assigneeIdSent = true;
        }

        public void setAddTags(@Nullable List<String> addTags) {
            this.addTags = addTags;
        }

        public void setRemoveTags(@Nullable List<String> removeTags) {
            this.removeTags = removeTags;
        }

        BulkUpdates.Changes toChanges() {
            return new BulkUpdates.Changes(
                    status == null ? null : TicketStatus.fromDb(status),
                    groupIdSent ? Patch.set(groupId) : Patch.keep(),
                    assigneeIdSent ? Patch.set(assigneeId) : Patch.keep(),
                    addTags == null ? List.of() : addTags,
                    removeTags == null ? List.of() : removeTags);
        }
    }

    record Failure(long number, String code) {}

    record BulkUpdateResponse(List<TicketResponse> updated, List<Failure> failed) {}

    /** 規則に合わないチケットは変えずに {@code failed} に入れ、残りは更新する。 */
    @PatchMapping("/api/t/{slug}/tickets")
    BulkUpdateResponse update(Viewer.Staff viewer, @Valid @RequestBody BulkUpdateRequest body) {
        var result = bulk.update(viewer, java.util.Objects.requireNonNull(body.numbers), body.toChanges());
        return new BulkUpdateResponse(
                result.updated().stream().map(TicketResponse::of).toList(),
                result.failed().stream()
                        .map(f -> new Failure(f.number(), f.code()))
                        .toList());
    }
}
