package dev.likezendesk.identity.infra;

import dev.likezendesk.shared.SqlCondition;
import dev.likezendesk.shared.Viewer;
import java.util.Map;

/** 閲覧者に見えるユーザーの条件。依頼者には自分だけが見える。 */
final class UserVisibility {

    private UserVisibility() {}

    /** {@code alias} はユーザー表の別名（例: "u"）。 */
    static SqlCondition of(Viewer viewer, String alias) {
        return switch (viewer) {
            case Viewer.Requester r ->
                new SqlCondition(
                        alias + ".tenant_id = :v_tenant AND " + alias + ".id = :v_user",
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
