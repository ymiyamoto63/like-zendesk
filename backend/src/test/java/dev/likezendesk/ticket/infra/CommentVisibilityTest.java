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

class CommentVisibilityTest {

    @Test
    void requesterSeesOnlyPublicCommentsOnOwnTickets() {
        assertThat(CommentVisibility.of(Viewers.REQUESTER, "c", "t"))
                .isEqualTo(new SqlCondition(
                        "(t.tenant_id = :v_tenant AND t.requester_id = :v_user) AND (c.public = true)",
                        Map.of("v_tenant", TENANT, "v_user", USER)));
    }

    static Stream<Viewer> tenantWideViewers() {
        return Stream.of(Viewers.AGENT, Viewers.ADMIN, Viewers.SYSTEM);
    }

    @ParameterizedTest
    @MethodSource("tenantWideViewers")
    void staffAndSystemSeeInternalNotesToo(Viewer viewer) {
        assertThat(CommentVisibility.of(viewer, "c", "t")).isEqualTo(TicketVisibility.of(viewer, "t"));
    }
}
