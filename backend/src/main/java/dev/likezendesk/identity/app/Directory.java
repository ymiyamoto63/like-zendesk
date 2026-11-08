package dev.likezendesk.identity.app;

import dev.likezendesk.identity.domain.GroupRef;
import dev.likezendesk.identity.domain.Role;
import dev.likezendesk.identity.domain.UserAccount;
import dev.likezendesk.identity.infra.GroupRepository;
import dev.likezendesk.identity.infra.UserRepository;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 他の機能が使う、ユーザーとグループの参照。呼び出し側のトランザクションに参加する。 */
@Service
public class Directory {

    static final int SEARCH_LIMIT = 20;

    private final UserRepository users;
    private final GroupRepository groups;

    Directory(UserRepository users, GroupRepository groups) {
        this.users = users;
        this.groups = groups;
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> user(Viewer viewer, UUID id) {
        return users.findById(viewer, new UserId(id));
    }

    @Transactional(readOnly = true)
    public Optional<GroupRef> group(Viewer viewer, UUID id) {
        return groups.find(viewer, id);
    }

    @Transactional(readOnly = true)
    public boolean isMember(Viewer viewer, UUID groupId, UUID userId) {
        return groups.isMember(viewer, groupId, new UserId(userId));
    }

    /** チケットの依頼者や担当者を選ぶための検索（F-T-1、F-T-6）。 */
    @Transactional(readOnly = true)
    public List<UserAccount> search(Viewer.Staff viewer, @Nullable Role role, String query) {
        return users.search(viewer, role, query.strip(), SEARCH_LIMIT);
    }
}
