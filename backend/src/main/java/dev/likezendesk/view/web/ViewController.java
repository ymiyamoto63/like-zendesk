package dev.likezendesk.view.web;

import dev.likezendesk.shared.Viewer;
import dev.likezendesk.ticket.domain.TicketFilter;
import dev.likezendesk.view.app.Views;
import dev.likezendesk.view.domain.View;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** ビュー（docs/design/api.md 2.3、3.5）。 */
@RestController
class ViewController {

    private final Views views;

    ViewController(Views views) {
        this.views = views;
    }

    record ConditionDto(
            @Schema(
                    allowableValues = {
                        "status",
                        "priority",
                        "type",
                        "assignee",
                        "group",
                        "organization",
                        "tags",
                        "created_at",
                        "updated_at"
                    })
            @NotBlank
            String field,

            @Schema(
                    allowableValues = {
                        "in",
                        "not_in",
                        "eq",
                        "is_null",
                        "is_not_null",
                        "has_any",
                        "has_all",
                        "within_days",
                        "older_than_days"
                    })
            @NotBlank
            String op,

            @Schema(description = "演算子による。in・has_any などは文字列の配列、eq は ID か @me、within_days は日数。is_null では省略") @Nullable
            Object value) {}

    record ConditionsDto(@NotNull @Size(max = 20) List<@Valid @NotNull ConditionDto> all) {}

    record SortDto(
            @Schema(allowableValues = {"number", "subject", "status", "priority", "type", "created_at", "updated_at"})
            @NotBlank
            String field,

            @Schema(allowableValues = {"asc", "desc"}) @Pattern(regexp = "asc|desc") @NotBlank
            String direction) {}

    record ViewResponse(
            String id,
            String name,

            @Schema(allowableValues = {"builtin", "shared", "personal"})
            String kind,

            ConditionsDto conditions,
            List<SortDto> sort,
            @Nullable Long count) {

        static ViewResponse of(View view, @Nullable Long count) {
            return new ViewResponse(
                    view.id(),
                    view.name(),
                    view.kind().dbValue(),
                    new ConditionsDto(view.filter().all().stream()
                            .map(c -> new ConditionDto(c.field(), c.op(), c.value()))
                            .toList()),
                    view.filter().sort().stream()
                            .map(s -> new SortDto(s.field(), s.descending() ? "desc" : "asc"))
                            .toList(),
                    count);
        }
    }

    record ViewRequest(
            @NotBlank @Size(max = 100) String name,

            @Schema(description = "true で共有ビューにする（管理者のみ）。作成時だけ使う") @Nullable
            Boolean shared,

            @NotNull @Valid ConditionsDto conditions,
            @Size(max = 3) @Nullable List<@Valid @NotNull SortDto> sort) {

        TicketFilter filter() {
            return new TicketFilter(
                    conditions.all().stream()
                            .map(c -> new TicketFilter.Condition(c.field(), c.op(), c.value()))
                            .toList(),
                    sort == null
                            ? List.of()
                            : sort.stream()
                                    .map(s -> new TicketFilter.Sort(s.field(), "desc".equals(s.direction())))
                                    .toList());
        }
    }

    @GetMapping("/api/t/{slug}/views")
    List<ViewResponse> list(Viewer.Staff viewer, @RequestParam(defaultValue = "false") boolean withCounts) {
        return views.list(viewer, withCounts).stream()
                .map(c -> ViewResponse.of(c.view(), c.count()))
                .toList();
    }

    @PostMapping("/api/t/{slug}/views")
    @ResponseStatus(HttpStatus.CREATED)
    ViewResponse create(Viewer.Staff viewer, @Valid @RequestBody ViewRequest body) {
        return ViewResponse.of(
                views.create(viewer, body.name(), body.filter(), Boolean.TRUE.equals(body.shared())), null);
    }

    @PatchMapping("/api/t/{slug}/views/{id}")
    ViewResponse update(Viewer.Staff viewer, @PathVariable String id, @Valid @RequestBody ViewRequest body) {
        return ViewResponse.of(views.update(viewer, id, body.name(), body.filter()), null);
    }

    @DeleteMapping("/api/t/{slug}/views/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Viewer.Staff viewer, @PathVariable String id) {
        views.delete(viewer, id);
    }
}
