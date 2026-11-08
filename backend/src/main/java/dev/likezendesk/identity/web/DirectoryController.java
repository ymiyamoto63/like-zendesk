package dev.likezendesk.identity.web;

import dev.likezendesk.identity.app.Directory;
import dev.likezendesk.identity.app.Groups;
import dev.likezendesk.identity.domain.Group;
import dev.likezendesk.identity.domain.Role;
import dev.likezendesk.shared.Viewer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** スタッフ向けのユーザー検索とグループ（F-T-1、F-T-6、F-A-3）。 */
@RestController
class DirectoryController {

    private final Directory directory;
    private final Groups groups;

    DirectoryController(Directory directory, Groups groups) {
        this.directory = directory;
        this.groups = groups;
    }

    record UserSummary(
            UUID id,
            String name,
            String email,

            @Schema(allowableValues = {"requester", "agent", "admin"})
            String role) {}

    @GetMapping("/api/t/{slug}/users")
    List<UserSummary> searchUsers(
            Viewer.Staff viewer,
            @RequestParam(required = false) @Nullable @Pattern(regexp = "requester|agent|admin") String role,
            @RequestParam(defaultValue = "") @Size(max = 100) String q) {
        return directory.search(viewer, role == null ? null : Role.fromDb(role), q).stream()
                .map(u -> new UserSummary(
                        u.id().value(), u.name(), u.email(), u.role().dbValue()))
                .toList();
    }

    record GroupResponse(UUID id, String name, List<Member> members) {
        record Member(UUID id, String name) {}

        static GroupResponse of(Group group) {
            return new GroupResponse(
                    group.id(),
                    group.name(),
                    group.members().stream()
                            .map(m -> new Member(m.id(), m.name()))
                            .toList());
        }
    }

    @GetMapping("/api/t/{slug}/groups")
    List<GroupResponse> listGroups(Viewer.Staff viewer) {
        return groups.list(viewer).stream().map(GroupResponse::of).toList();
    }

    record CreateGroupRequest(@NotBlank @Size(max = 100) String name) {}

    record GroupCreated(UUID id, String name) {}

    @PostMapping("/api/t/{slug}/admin/groups")
    @ResponseStatus(HttpStatus.CREATED)
    GroupCreated createGroup(Viewer.Admin viewer, @Valid @RequestBody CreateGroupRequest body) {
        var group = groups.create(viewer, body.name());
        return new GroupCreated(group.id(), group.name());
    }

    @PatchMapping("/api/t/{slug}/admin/groups/{id}")
    GroupCreated renameGroup(Viewer.Admin viewer, @PathVariable UUID id, @Valid @RequestBody CreateGroupRequest body) {
        var group = groups.rename(viewer, id, body.name());
        return new GroupCreated(group.id(), group.name());
    }

    @DeleteMapping("/api/t/{slug}/admin/groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteGroup(Viewer.Admin viewer, @PathVariable UUID id) {
        groups.delete(viewer, id);
    }

    record MembersRequest(@NotNull @Size(max = 500) List<@NotNull UUID> userIds) {}

    @PutMapping("/api/t/{slug}/admin/groups/{id}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void replaceMembers(Viewer.Admin viewer, @PathVariable UUID id, @Valid @RequestBody MembersRequest body) {
        groups.replaceMembers(viewer, id, body.userIds());
    }
}
