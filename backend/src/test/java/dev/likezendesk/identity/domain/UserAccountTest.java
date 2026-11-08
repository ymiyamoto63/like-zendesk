package dev.likezendesk.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.likezendesk.shared.TenantId;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserAccountTest {

    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final UserId USER = new UserId(UUID.randomUUID());

    private static UserAccount withRole(Role role) {
        return new UserAccount(TENANT, USER, "a@example.com", "A", role, true, true, "hash");
    }

    @Test
    void viewerMatchesTheRole() {
        assertThat(withRole(Role.REQUESTER).viewer()).isEqualTo(new Viewer.Requester(TENANT, USER));
        assertThat(withRole(Role.AGENT).viewer()).isEqualTo(new Viewer.Agent(TENANT, USER));
        assertThat(withRole(Role.ADMIN).viewer()).isEqualTo(new Viewer.Admin(TENANT, USER));
    }

    @Test
    void emailAddressesAreComparedCaseInsensitively() {
        assertThat(EmailAddresses.normalize("  Sato@Example.COM ")).isEqualTo("sato@example.com");
    }
}
