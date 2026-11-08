package dev.likezendesk.identity.infra;

import dev.likezendesk.identity.domain.Group;
import dev.likezendesk.identity.domain.GroupRef;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** グループと所属。依頼者には見せないので、閲覧者はスタッフかシステムに限る。 */
@Repository
public class GroupRepository {

    private final JdbcClient jdbc;

    GroupRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 全グループと所属メンバー（有効なユーザーだけ）。名前順。 */
    public List<Group> list(Viewer.Staff viewer) {
        var groups = new LinkedHashMap<UUID, Group>();
        var members = new LinkedHashMap<UUID, List<Group.Member>>();
        jdbc.sql("""
                        SELECT g.id, g.name, u.id AS user_id, u.name AS user_name
                        FROM agent_groups g
                        LEFT JOIN group_memberships m ON m.tenant_id = g.tenant_id AND m.group_id = g.id
                        LEFT JOIN users u ON u.tenant_id = m.tenant_id AND u.id = m.user_id AND u.active
                        WHERE g.tenant_id = :v_tenant
                        ORDER BY g.name, u.name
                        """).param("v_tenant", viewer.tenantId().value()).query(rs -> {
            var id = rs.getObject("id", UUID.class);
            groups.putIfAbsent(id, new Group(id, rs.getString("name"), List.of()));
            var list = members.computeIfAbsent(id, k -> new ArrayList<>());
            var userId = rs.getObject("user_id", UUID.class);
            if (userId != null) {
                list.add(new Group.Member(userId, rs.getString("user_name")));
            }
        });
        return groups.values().stream()
                .map(g -> new Group(g.id(), g.name(), members.get(g.id())))
                .toList();
    }

    public Optional<GroupRef> find(Viewer viewer, UUID id) {
        return jdbc.sql("SELECT id, name FROM agent_groups WHERE tenant_id = :v_tenant AND id = :id")
                .param("v_tenant", staffTenant(viewer))
                .param("id", id)
                .query((rs, row) -> new GroupRef(rs.getObject("id", UUID.class), rs.getString("name")))
                .optional();
    }

    public boolean isMember(Viewer viewer, UUID groupId, UserId userId) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM group_memberships
                                       WHERE tenant_id = :v_tenant AND group_id = :group AND user_id = :user)
                        """)
                .param("v_tenant", staffTenant(viewer))
                .param("group", groupId)
                .param("user", userId.value())
                .query(Boolean.class)
                .single();
    }

    /** 同じ名前のグループがあれば {@link org.springframework.dao.DuplicateKeyException}。 */
    public GroupRef insert(Viewer.Admin viewer, String name) {
        var id = jdbc.sql("INSERT INTO agent_groups (tenant_id, name) VALUES (:v_tenant, :name) RETURNING id")
                .param("v_tenant", viewer.tenantId().value())
                .param("name", name)
                .query(UUID.class)
                .single();
        return new GroupRef(id, name);
    }

    /** 所属メンバーを置き換える。{@code userIds} は有効なスタッフであること（呼び出し側で確かめる）。 */
    public void replaceMembers(Viewer.Admin viewer, UUID groupId, List<UserId> userIds) {
        var ids = userIds.stream().map(UserId::value).toArray(UUID[]::new);
        jdbc.sql("""
                        DELETE FROM group_memberships
                        WHERE tenant_id = :v_tenant AND group_id = :group AND NOT (user_id = ANY (:ids))
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .param("group", groupId)
                .param("ids", ids)
                .update();
        jdbc.sql("""
                        INSERT INTO group_memberships (tenant_id, group_id, user_id)
                        SELECT :v_tenant, :group, unnest(CAST(:ids AS uuid[]))
                        ON CONFLICT DO NOTHING
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .param("group", groupId)
                .param("ids", ids)
                .update();
    }

    private static UUID staffTenant(Viewer viewer) {
        return switch (viewer) {
            case Viewer.Staff s -> s.tenantId().value();
            case Viewer.System s -> s.tenantId().value();
            // 依頼者にはグループを見せない。存在しない ID と同じ結果になる条件にする
            case Viewer.Requester _ -> new UUID(0, 0);
        };
    }
}
