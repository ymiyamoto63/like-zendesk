package dev.likezendesk.identity.app;

import dev.likezendesk.identity.domain.Group;
import dev.likezendesk.identity.domain.GroupRef;
import dev.likezendesk.identity.domain.Role;
import dev.likezendesk.identity.infra.GroupRepository;
import dev.likezendesk.identity.infra.UserRepository;
import dev.likezendesk.shared.ApiException;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** エージェントのグループ（F-A-3）。 */
@Service
public class Groups {

    private final GroupRepository groups;
    private final UserRepository users;

    Groups(GroupRepository groups, UserRepository users) {
        this.groups = groups;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<Group> list(Viewer.Staff viewer) {
        return groups.list(viewer);
    }

    @Transactional
    public GroupRef create(Viewer.Admin viewer, String name) {
        try {
            return groups.insert(viewer, name.strip());
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("group.name_taken", "A group with this name already exists");
        }
    }

    /** 所属メンバーを置き換える。メンバーは有効なエージェントか管理者に限る。 */
    @Transactional
    public void replaceMembers(Viewer.Admin viewer, UUID groupId, List<UUID> userIds) {
        groups.find(viewer, groupId).orElseThrow(ApiException::notFound);
        var members = userIds.stream().distinct().map(UserId::new).toList();
        for (var id : members) {
            var user = users.findById(viewer, id).orElseThrow(() -> invalidMember());
            if (!user.active() || user.role() == Role.REQUESTER) {
                throw invalidMember();
            }
        }
        try {
            groups.replaceMembers(viewer, groupId, members);
        } catch (DataIntegrityViolationException e) {
            // 外したメンバーが担当する解決済みチケットは、担当者を外せない（data-model.md 3.3）
            throw ApiException.conflict(
                    "group.member_has_solved_tickets", "A removed member is the assignee of solved tickets");
        }
    }

    private static ApiException invalidMember() {
        return ApiException.rule("group.member_must_be_staff", "Members must be active agents or admins");
    }
}
