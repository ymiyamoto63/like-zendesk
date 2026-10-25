package dev.likezendesk.ticket.infra;

import static dev.likezendesk.ticket.infra.Viewers.TENANT;
import static dev.likezendesk.ticket.infra.Viewers.USER;
import static org.assertj.core.api.Assertions.assertThat;

import dev.likezendesk.shared.SqlCondition;
import dev.likezendesk.shared.Viewer;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TicketVisibilityTest {

    @Test
    void requesterSeesOnlyOwnTicketsInTenant() {
        assertThat(TicketVisibility.of(Viewers.REQUESTER, "t"))
                .isEqualTo(new SqlCondition(
                        "t.tenant_id = :v_tenant AND t.requester_id = :v_user",
                        Map.of("v_tenant", TENANT, "v_user", USER)));
    }

    static Stream<Viewer> tenantWideViewers() {
        return Stream.of(Viewers.AGENT, Viewers.ADMIN, Viewers.SYSTEM);
    }

    @ParameterizedTest
    @MethodSource("tenantWideViewers")
    void staffAndSystemSeeEveryTicketInTenant(Viewer viewer) {
        assertThat(TicketVisibility.of(viewer, "t"))
                .isEqualTo(new SqlCondition("t.tenant_id = :v_tenant", Map.of("v_tenant", TENANT)));
    }

    @Test
    void usesTheGivenAlias() {
        assertThat(TicketVisibility.of(Viewers.AGENT, "tk").sql()).isEqualTo("tk.tenant_id = :v_tenant");
    }
}
