package dev.likezendesk.tenant.web;

import dev.likezendesk.shared.Viewer;
import dev.likezendesk.tenant.app.Tenants;
import dev.likezendesk.tenant.domain.RequesterSignup;
import dev.likezendesk.tenant.domain.Tenant;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 管理者によるテナント設定（F-X-6、F-A-2）。 */
@RestController
class TenantSettingsController {

    private static final String DOMAIN = "^@?[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$";

    private final Tenants tenants;

    TenantSettingsController(Tenants tenants) {
        this.tenants = tenants;
    }

    record SettingsResponse(
            String name,

            @Schema(allowableValues = {"open", "domain_restricted"})
            String requesterSignup,

            List<String> allowedDomains) {

        static SettingsResponse of(Tenant tenant) {
            return new SettingsResponse(tenant.name(), tenant.requesterSignup().dbValue(), tenant.allowedDomains());
        }
    }

    record SettingsRequest(
            @NotBlank @Size(max = 100) String name,

            @NotNull
            @Schema(allowableValues = {"open", "domain_restricted"})
            @Pattern(regexp = "open|domain_restricted")
            String requesterSignup,

            @NotNull @Size(max = 50) List<@NotBlank @Pattern(regexp = DOMAIN) String> allowedDomains) {}

    @GetMapping("/api/t/{slug}/admin/settings")
    SettingsResponse get(Viewer.Admin viewer) {
        return SettingsResponse.of(tenants.settings(viewer));
    }

    @PatchMapping("/api/t/{slug}/admin/settings")
    SettingsResponse update(Viewer.Admin viewer, @Valid @RequestBody SettingsRequest body) {
        var domains = body.allowedDomains().stream()
                .map(d -> d.startsWith("@") ? d.substring(1) : d)
                .toList();
        return SettingsResponse.of(
                tenants.updateSettings(viewer, body.name(), RequesterSignup.fromDb(body.requesterSignup()), domains));
    }
}
