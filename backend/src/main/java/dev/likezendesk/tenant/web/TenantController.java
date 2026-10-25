package dev.likezendesk.tenant.web;

import dev.likezendesk.tenant.app.Tenants;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TenantController {

    private final Tenants tenants;

    TenantController(Tenants tenants) {
        this.tenants = tenants;
    }

    record TenantInfo(
            String slug,
            String name,

            @Schema(allowableValues = {"open", "domain_restricted"})
            String requesterSignup) {}

    /** ログイン画面とポータルの表示用。ログインは要らない。 */
    @GetMapping("/api/t/{slug}")
    TenantInfo get(@PathVariable String slug) {
        var tenant = tenants.require(slug);
        return new TenantInfo(
                tenant.slug(), tenant.name(), tenant.requesterSignup().dbValue());
    }
}
