package dev.likezendesk.tenant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.likezendesk.shared.TenantId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantTest {

    private static Tenant tenant(RequesterSignup signup, String... domains) {
        return new Tenant(new TenantId(UUID.randomUUID()), "acme", "Acme", signup, List.of(domains));
    }

    @Test
    void openTenantAcceptsAnyEmail() {
        assertThat(tenant(RequesterSignup.OPEN).acceptsRequesterEmail("a@anywhere.org"))
                .isTrue();
    }

    @Test
    void domainRestrictedTenantAcceptsOnlyListedDomains() {
        var tenant = tenant(RequesterSignup.DOMAIN_RESTRICTED, "example.com", "example.co.jp");
        assertThat(tenant.acceptsRequesterEmail("a@example.com")).isTrue();
        assertThat(tenant.acceptsRequesterEmail("a@example.co.jp")).isTrue();
        assertThat(tenant.acceptsRequesterEmail("a@sub.example.com")).isFalse();
        assertThat(tenant.acceptsRequesterEmail("a@example.com.evil.org")).isFalse();
    }
}
