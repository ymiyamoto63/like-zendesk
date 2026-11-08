package dev.likezendesk.identity.web;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.likezendesk.identity.app.Organizations;
import dev.likezendesk.identity.app.UserAdministration;
import dev.likezendesk.identity.domain.AdminUser;
import dev.likezendesk.identity.domain.Organization;
import dev.likezendesk.identity.domain.Role;
import dev.likezendesk.shared.Viewer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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

/** 管理画面のユーザーと所属組織（F-A-1、F-A-4）。組織の一覧は、ビューの条件に使うのでスタッフ全員が読める。 */
@RestController
class AdminController {

    private final UserAdministration users;
    private final Organizations organizations;

    AdminController(UserAdministration users, Organizations organizations) {
        this.users = users;
        this.organizations = organizations;
    }

    record OrganizationRef(UUID id, String name) {}

    record AdminUserResponse(
            UUID id,
            String name,
            String email,

            @Schema(allowableValues = {"requester", "agent", "admin"})
            String role,

            @Schema(allowableValues = {"invited", "unverified", "active", "inactive"})
            String status,

            @Nullable OrganizationRef organization,
            Instant createdAt) {

        static AdminUserResponse of(AdminUser user) {
            var account = user.account();
            var org = user.organization();
            return new AdminUserResponse(
                    account.id().value(),
                    account.name(),
                    account.email(),
                    account.role().dbValue(),
                    user.status().dbValue(),
                    org == null ? null : new OrganizationRef(org.id(), org.name()),
                    user.createdAt());
        }
    }

    record AdminUserPage(List<AdminUserResponse> items, long total, int page, int size) {}

    @GetMapping("/api/t/{slug}/admin/users")
    AdminUserPage listUsers(
            Viewer.Admin viewer,
            @RequestParam(required = false) @Nullable @Pattern(regexp = "requester|agent|admin") String role,
            @RequestParam(required = false) @Nullable @Pattern(regexp = "invited|unverified|active|inactive")
                    String status,
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        var query = new AdminUser.Query(
                role == null ? null : Role.fromDb(role),
                status == null ? null : AdminUser.Status.valueOf(status.toUpperCase(Locale.ROOT)),
                q.strip());
        var result = users.list(viewer, query, page, size);
        return new AdminUserPage(
                result.items().stream().map(AdminUserResponse::of).toList(), result.total(), page, size);
    }

    /** 送った項目だけを変える。role は agent と admin の切り替えだけ、organizationId は依頼者だけ。 */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static final class UpdateUserRequest {
        @Schema(allowableValues = {"agent", "admin"})
        @Pattern(regexp = "agent|admin")
        private @Nullable String role;

        private @Nullable Boolean active;

        @Schema(
                types = {"string", "null"},
                format = "uuid",
                description = "null で所属なしにする。省略すると変えない")
        private @Nullable UUID organizationId;

        @JsonIgnore
        private boolean organizationIdSent;

        public void setRole(@Nullable String role) {
            this.role = role;
        }

        public void setActive(@Nullable Boolean active) {
            this.active = active;
        }

        public void setOrganizationId(@Nullable UUID organizationId) {
            this.organizationId = organizationId;
            this.organizationIdSent = true;
        }

        UserAdministration.Changes toChanges() {
            return new UserAdministration.Changes(
                    role == null ? null : Role.fromDb(role), active, organizationIdSent, organizationId);
        }
    }

    @PatchMapping("/api/t/{slug}/admin/users/{id}")
    AdminUserResponse updateUser(
            Viewer.Admin viewer, @PathVariable UUID id, @Valid @RequestBody UpdateUserRequest body) {
        return AdminUserResponse.of(users.update(viewer, id, body.toChanges()));
    }

    record OrganizationResponse(UUID id, String name, long members) {
        static OrganizationResponse of(Organization o) {
            return new OrganizationResponse(o.id(), o.name(), o.members());
        }
    }

    record OrganizationRequest(@NotBlank @Size(max = 100) String name) {}

    @GetMapping("/api/t/{slug}/organizations")
    List<OrganizationResponse> listOrganizations(Viewer.Staff viewer) {
        return organizations.list(viewer).stream().map(OrganizationResponse::of).toList();
    }

    @GetMapping("/api/t/{slug}/admin/organizations")
    List<OrganizationResponse> adminOrganizations(Viewer.Admin viewer) {
        return listOrganizations(viewer);
    }

    @PostMapping("/api/t/{slug}/admin/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    OrganizationResponse createOrganization(Viewer.Admin viewer, @Valid @RequestBody OrganizationRequest body) {
        return OrganizationResponse.of(organizations.create(viewer, body.name()));
    }

    @PatchMapping("/api/t/{slug}/admin/organizations/{id}")
    OrganizationResponse renameOrganization(
            Viewer.Admin viewer, @PathVariable UUID id, @Valid @RequestBody OrganizationRequest body) {
        return OrganizationResponse.of(organizations.rename(viewer, id, body.name()));
    }

    @DeleteMapping("/api/t/{slug}/admin/organizations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteOrganization(Viewer.Admin viewer, @PathVariable UUID id) {
        organizations.delete(viewer, id);
    }
}
