package dev.likezendesk.ticket.infra;

import dev.likezendesk.shared.SqlCondition;
import dev.likezendesk.shared.Viewer;
import java.util.Map;

/** 閲覧者に見えるチケットの条件。チケット表を読み書きする全ての SQL に結合する。 */
final class TicketVisibility {

    private TicketVisibility() {}

    /** {@code alias} はチケット表の別名（例: "t"）。 */
    static SqlCondition of(Viewer viewer, String alias) {
        return switch (viewer) {
            case Viewer.Requester r ->
                new SqlCondition(
                        alias + ".tenant_id = :v_tenant AND " + alias + ".requester_id = :v_user",
                        Map.of(
                                "v_tenant",
                                r.tenantId().value(),
                                "v_user",
                                r.userId().value()));
            case Viewer.Staff s ->
                new SqlCondition(
                        alias + ".tenant_id = :v_tenant",
                        Map.of("v_tenant", s.tenantId().value()));
            case Viewer.System s ->
                new SqlCondition(
                        alias + ".tenant_id = :v_tenant",
                        Map.of("v_tenant", s.tenantId().value()));
        };
    }
}
