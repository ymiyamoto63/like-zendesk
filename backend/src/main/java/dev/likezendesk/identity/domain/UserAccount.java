package dev.likezendesk.identity.domain;

import dev.likezendesk.shared.TenantId;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import org.jspecify.annotations.Nullable;

/**
 * ログインできる利用者。{@code passwordHash} は招待を受諾するまで null。依頼者は {@code emailVerified} になるまでログインできない。
 */
public record UserAccount(
        TenantId tenantId,
        UserId id,
        String email,
        String name,
        Role role,
        boolean active,
        boolean emailVerified,
        @Nullable String passwordHash) {

    /** この利用者として操作するときの閲覧者。ロールは毎回 DB から読むので、変更や無効化はすぐに効く。 */
    public Viewer viewer() {
        return switch (role) {
            case REQUESTER -> new Viewer.Requester(tenantId, id);
            case AGENT -> new Viewer.Agent(tenantId, id);
            case ADMIN -> new Viewer.Admin(tenantId, id);
        };
    }
}
