package dev.likezendesk.view.infra;

import dev.likezendesk.shared.Viewer;
import dev.likezendesk.ticket.domain.TicketFilter;
import dev.likezendesk.view.domain.View;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 共有ビューと個人ビュー。閲覧者には、共有ビューと自分の個人ビューだけが見える。 */
@Repository
public class ViewRepository {

    private static final String VISIBLE = "v.tenant_id = :v_tenant AND (v.owner_id IS NULL OR v.owner_id = :v_user)";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    ViewRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** 共有ビュー、個人ビューの順。それぞれ作成順。 */
    public List<View> list(Viewer.Staff viewer) {
        return jdbc.sql("SELECT v.id, v.owner_id, v.name, v.conditions, v.sort FROM views v WHERE " + VISIBLE
                        + " ORDER BY v.owner_id IS NOT NULL, v.position, v.id")
                .param("v_tenant", viewer.tenantId().value())
                .param("v_user", viewer.userId().value())
                .query(this::map)
                .list();
    }

    public Optional<View> find(Viewer.Staff viewer, UUID id) {
        return jdbc.sql("SELECT v.id, v.owner_id, v.name, v.conditions, v.sort FROM views v WHERE " + VISIBLE
                        + " AND v.id = :id")
                .param("v_tenant", viewer.tenantId().value())
                .param("v_user", viewer.userId().value())
                .param("id", id)
                .query(this::map)
                .optional();
    }

    /** {@code ownerId} が null なら共有ビュー。 */
    public UUID insert(Viewer.Staff viewer, @Nullable UUID ownerId, String name, TicketFilter filter) {
        return jdbc.sql("""
                        INSERT INTO views (tenant_id, owner_id, name, conditions, sort)
                        VALUES (:v_tenant, :owner, :name, CAST(:conditions AS jsonb), CAST(:sort AS jsonb))
                        RETURNING id
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .param("owner", ownerId)
                .param("name", name)
                .param("conditions", conditionsJson(filter))
                .param("sort", sortJson(filter))
                .query(UUID.class)
                .single();
    }

    /** 見えるビューだけを変える。変えられなければ false。編集権限の判定は呼び出し側で行う。 */
    public boolean update(Viewer.Staff viewer, UUID id, String name, TicketFilter filter) {
        return jdbc.sql("UPDATE views v SET name = :name, conditions = CAST(:conditions AS jsonb),"
                                + " sort = CAST(:sort AS jsonb) WHERE " + VISIBLE + " AND v.id = :id")
                        .param("v_tenant", viewer.tenantId().value())
                        .param("v_user", viewer.userId().value())
                        .param("id", id)
                        .param("name", name)
                        .param("conditions", conditionsJson(filter))
                        .param("sort", sortJson(filter))
                        .update()
                == 1;
    }

    public boolean delete(Viewer.Staff viewer, UUID id) {
        return jdbc.sql("DELETE FROM views v WHERE " + VISIBLE + " AND v.id = :id")
                        .param("v_tenant", viewer.tenantId().value())
                        .param("v_user", viewer.userId().value())
                        .param("id", id)
                        .update()
                == 1;
    }

    private String conditionsJson(TicketFilter filter) {
        var all = filter.all().stream()
                .map(c -> {
                    var m = new java.util.LinkedHashMap<String, Object>();
                    m.put("field", c.field());
                    m.put("op", c.op());
                    if (c.value() != null) {
                        m.put("value", c.value());
                    }
                    return m;
                })
                .toList();
        return json.writeValueAsString(Map.of("all", all));
    }

    private String sortJson(TicketFilter filter) {
        return json.writeValueAsString(filter.sort().stream()
                .map(s -> Map.of("field", s.field(), "direction", s.descending() ? "desc" : "asc"))
                .toList());
    }

    private View map(ResultSet rs, int row) throws SQLException {
        Map<String, List<Map<String, Object>>> conditions =
                json.readValue(rs.getString("conditions"), new TypeReference<>() {});
        List<Map<String, String>> sort = json.readValue(rs.getString("sort"), new TypeReference<>() {});
        var filter = new TicketFilter(
                conditions.getOrDefault("all", List.of()).stream()
                        .map(c -> new TicketFilter.Condition(
                                (String) c.get("field"), (String) c.get("op"), c.get("value")))
                        .toList(),
                sort.stream()
                        .map(s -> new TicketFilter.Sort(s.get("field"), "desc".equals(s.get("direction"))))
                        .toList());
        var owner = rs.getObject("owner_id", UUID.class);
        return new View(
                rs.getObject("id", UUID.class).toString(),
                rs.getString("name"),
                owner == null ? View.Kind.SHARED : View.Kind.PERSONAL,
                filter,
                owner);
    }
}
