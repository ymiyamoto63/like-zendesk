package dev.likezendesk.identity.app;

import dev.likezendesk.identity.domain.AdminUser;
import dev.likezendesk.identity.domain.Role;
import dev.likezendesk.identity.infra.OrganizationRepository;
import dev.likezendesk.identity.infra.UserRepository;
import dev.likezendesk.shared.ApiException;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理者によるユーザーの管理（F-A-1、F-A-4）: 一覧、エージェントと管理者の切り替え、有効・無効、依頼者の所属組織。
 *
 * <p>依頼者とスタッフの間でロールは変えない（ポータルとエージェント画面で扱うデータが違うため）。自分自身の降格・無効化と、最後の管理者を失う変更は拒否する。
 */
@Service
public class UserAdministration {

    private final UserRepository users;
    private final OrganizationRepository organizations;

    UserAdministration(UserRepository users, OrganizationRepository organizations) {
        this.users = users;
        this.organizations = organizations;
    }

    public record Page(List<AdminUser> items, long total) {}

    /** 変更。null の項目は変えない。{@code organizationId} は {@code organizationSent} のときだけ使う（null で所属なし）。 */
    public record Changes(
            @Nullable Role role,
            @Nullable Boolean active,
            boolean organizationSent,
            @Nullable UUID organizationId) {}

    @Transactional(readOnly = true)
    public Page list(Viewer.Admin viewer, AdminUser.Query query, int page, int size) {
        return new Page(users.adminList(viewer, query, page, size), users.adminCount(viewer, query));
    }

    @Transactional
    public AdminUser update(Viewer.Admin viewer, UUID id, Changes changes) {
        var userId = new UserId(id);
        var user = users.findById(viewer, userId).orElseThrow(ApiException::notFound);
        var self = viewer.userId().equals(userId);

        if (changes.role() != null && changes.role() != user.role()) {
            if (user.role() == Role.REQUESTER || changes.role() == Role.REQUESTER) {
                throw ApiException.rule("user.role_change_not_allowed", "Requesters and staff cannot switch roles");
            }
            if (self) {
                throw ApiException.rule("user.cannot_change_self", "You cannot change your own role");
            }
            users.changeRole(viewer, userId, changes.role());
        }
        if (changes.active() != null && changes.active() != user.active()) {
            if (self) {
                throw ApiException.rule("user.cannot_change_self", "You cannot deactivate yourself");
            }
            users.setActive(viewer, userId, changes.active());
        }
        if (changes.organizationSent()) {
            if (user.role() != Role.REQUESTER) {
                throw ApiException.rule("user.organization_for_requesters", "Only requesters belong to organizations");
            }
            if (changes.organizationId() != null
                    && organizations.find(viewer, changes.organizationId()).isEmpty()) {
                throw ApiException.rule("organization.not_found", "Organization not found");
            }
            users.setOrganization(viewer, userId, changes.organizationId());
        }
        if (users.activeAdmins(viewer) == 0) {
            // 自分は変えられないので通常は起こらない。起きたら例外でロールバックする
            throw ApiException.rule("user.last_admin", "The tenant needs at least one active admin");
        }
        return users.adminFind(viewer, userId).orElseThrow();
    }
}
