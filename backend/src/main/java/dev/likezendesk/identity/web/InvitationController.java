package dev.likezendesk.identity.web;

import dev.likezendesk.identity.app.Invitations;
import dev.likezendesk.identity.app.Profiles;
import dev.likezendesk.identity.domain.Role;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.tenant.app.Tenants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** エージェントと管理者の招待（F-A-1）。 */
@RestController
class InvitationController {

    private final Tenants tenants;
    private final Invitations invitations;
    private final Profiles profiles;

    InvitationController(Tenants tenants, Invitations invitations, Profiles profiles) {
        this.tenants = tenants;
        this.invitations = invitations;
        this.profiles = profiles;
    }

    record InviteRequest(
            @NotBlank @Email @Size(max = 254) String email,

            @NotNull @Schema(allowableValues = {"agent", "admin"}) @Pattern(regexp = "agent|admin")
            String role) {}

    @PostMapping("/api/t/{slug}/admin/invitations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void invite(@PathVariable String slug, Viewer.Admin viewer, @Valid @RequestBody InviteRequest body) {
        invitations.invite(viewer, tenants.require(slug), body.email(), Role.fromDb(body.role()));
    }

    record InvitationResponse(String email) {}

    @GetMapping("/api/t/{slug}/invitations/{token}")
    InvitationResponse describe(@PathVariable String slug, @PathVariable String token) {
        return new InvitationResponse(invitations.invitedEmail(tenants.require(slug), token));
    }

    record AcceptRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    /** 招待を受諾し、そのユーザーとしてログインする。 */
    @PostMapping("/api/t/{slug}/invitations/{token}/accept")
    MeResponse accept(
            @PathVariable String slug,
            @PathVariable String token,
            @Valid @RequestBody AcceptRequest body,
            HttpServletRequest request) {
        var tenant = tenants.require(slug);
        var me = profiles.me(invitations.accept(tenant, token, body.name(), body.password()));
        LoginSessions.login(request, tenant.id(), me.id());
        return MeResponse.of(me);
    }
}
