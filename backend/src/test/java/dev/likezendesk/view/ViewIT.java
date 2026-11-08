package dev.likezendesk.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.likezendesk.notification.app.MailDispatcher;
import dev.likezendesk.support.ApiClient;
import dev.likezendesk.support.MailTestConfiguration;
import dev.likezendesk.support.PostgresTestConfiguration;
import dev.likezendesk.support.RecordingMailSender;
import dev.likezendesk.support.TenantFixture;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;

/** ビュー・全文検索・並べ替え（F-V-1〜5）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestConfiguration.class, MailTestConfiguration.class})
class ViewIT {

    @LocalServerPort
    int port;

    @Autowired
    MailDispatcher dispatcher;

    @Autowired
    RecordingMailSender mails;

    TenantFixture tenant;
    TenantFixture.User sato;
    TenantFixture.User suzuki;
    TenantFixture.User requester;
    UUID infra;

    @BeforeEach
    void setUp() {
        tenant = new TenantFixture(port, dispatcher, mails);
        sato = tenant.agent("佐藤 一郎");
        suzuki = tenant.agent("鈴木 次郎");
        requester = tenant.requester("山田 花子");
        infra = tenant.group("インフラ", sato);

        // 1: 佐藤の担当、2: 未割り当て（コメントに「プリンター」）、3: 鈴木が解決済み
        create(Map.of(
                "subject", "VPN に接続できない",
                "priority", "high",
                "groupId", infra.toString(),
                "assigneeId", sato.id().toString(),
                "tags", List.of("vpn")));
        create(Map.of("subject", "メールが届かない", "priority", "low"));
        assertThat(sato.browser()
                        .post(tenant.path("/tickets/2/comments"), Map.of("body", "3 階のプリンターも使えない", "public", false))
                        .status())
                .isEqualTo(201);
        create(Map.of("subject", "パスワードを忘れた 100%"));
        assertThat(sato.browser()
                        .patch(
                                tenant.path("/tickets/3"),
                                Map.of(
                                        "version",
                                        0,
                                        "status",
                                        "solved",
                                        "groupId",
                                        tenant.group("総務", suzuki).toString(),
                                        "assigneeId",
                                        suzuki.id().toString()))
                        .status())
                .isEqualTo(200);
    }

    private void create(Map<String, Object> extra) {
        var body =
                new HashMap<String, Object>(Map.of("requesterId", requester.id().toString(), "body", "困っています。"));
        body.putAll(extra);
        assertThat(sato.browser().post(tenant.path("/tickets"), body).status()).isEqualTo(201);
    }

    private static List<Long> numbers(ApiClient.Response response) {
        assertThat(response.status()).isEqualTo(200);
        return StreamSupport.stream(response.body().path("items").spliterator(), false)
                .map(t -> t.path("number").asLong())
                .toList();
    }

    private List<Long> tickets(ApiClient browser, String query) {
        return numbers(browser.get(tenant.path("/tickets?" + query)));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static JsonNode view(JsonNode views, String id) {
        return StreamSupport.stream(views.spliterator(), false)
                .filter(v -> v.path("id").asString().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, Object> viewBody(String name, Map<String, Object> condition, Object... extra) {
        var body = new HashMap<String, Object>(Map.of("name", name, "conditions", Map.of("all", List.of(condition))));
        for (int i = 0; i < extra.length; i += 2) {
            body.put((String) extra[i], extra[i + 1]);
        }
        return body;
    }

    @Test
    void builtinViewsListTicketsWithCounts() {
        var views = sato.browser().get(tenant.path("/views?withCounts=true"));
        assertThat(views.status()).isEqualTo(200);
        assertThat(view(views.body(), "builtin:my-unsolved").path("count").asLong())
                .isEqualTo(1);
        assertThat(view(views.body(), "builtin:unassigned").path("count").asLong())
                .isEqualTo(1);
        assertThat(view(views.body(), "builtin:my-groups-unsolved")
                        .path("count")
                        .asLong())
                .isEqualTo(1);
        assertThat(view(views.body(), "builtin:all").path("count").asLong()).isEqualTo(3);
        assertThat(view(views.body(), "builtin:all").path("kind").asString()).isEqualTo("builtin");

        // 鈴木の担当は解決済みだけ。
        var suzukis =
                suzuki.browser().get(tenant.path("/views?withCounts=true")).body();
        assertThat(view(suzukis, "builtin:my-unsolved").path("count").asLong()).isZero();
        assertThat(view(suzukis, "builtin:my-groups-unsolved").path("count").asLong())
                .isZero();

        assertThat(sato.browser()
                        .get(tenant.path("/views"))
                        .body()
                        .get(0)
                        .path("count")
                        .isNull())
                .isTrue();

        assertThat(tickets(sato.browser(), "view=builtin:my-unsolved")).containsExactly(1L);
        assertThat(tickets(sato.browser(), "view=builtin:unassigned")).containsExactly(2L);
        assertThat(tickets(sato.browser(), "")).containsExactly(3L, 2L, 1L);
    }

    @Test
    void searchMatchesSubjectsAndCommentsInJapanese() {
        assertThat(tickets(sato.browser(), "q=" + encode("接続"))).containsExactly(1L);
        assertThat(tickets(sato.browser(), "q=" + encode("プリンター"))).containsExactly(2L);
        assertThat(tickets(sato.browser(), "q=" + encode("困って"))).hasSize(3);
        assertThat(tickets(sato.browser(), "q=" + encode("100%"))).containsExactly(3L);
        assertThat(tickets(sato.browser(), "q=" + encode("%"))).containsExactly(3L);
        assertThat(tickets(sato.browser(), "view=builtin:unassigned&q=" + encode("接続")))
                .isEmpty();
    }

    @Test
    void sortsByColumns() {
        assertThat(tickets(sato.browser(), "sort=-priority")).containsExactly(1L, 3L, 2L);
        assertThat(tickets(sato.browser(), "sort=priority")).containsExactly(2L, 3L, 1L);
        assertThat(tickets(sato.browser(), "sort=number")).containsExactly(1L, 2L, 3L);
        assertThat(tickets(sato.browser(), "sort=-createdAt&size=2&page=1")).containsExactly(1L);

        var unknown = sato.browser().get(tenant.path("/tickets?sort=requester"));
        assertThat(unknown.status()).isEqualTo(422);
        assertThat(unknown.code()).isEqualTo("view.invalid_sort");
        assertThat(sato.browser().get(tenant.path("/tickets?sort=a;drop")).status())
                .isEqualTo(400);
    }

    @Test
    void agentsKeepPersonalViews() {
        var created = sato.browser()
                .post(
                        tenant.path("/views"),
                        viewBody(
                                "VPN",
                                Map.of("field", "tags", "op", "has_any", "value", List.of("VPN")),
                                "sort",
                                List.of(Map.of("field", "number", "direction", "asc"))));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().path("kind").asString()).isEqualTo("personal");
        assertThat(created.body()
                        .path("conditions")
                        .path("all")
                        .get(0)
                        .path("value")
                        .get(0)
                        .asString())
                .isEqualTo("vpn");
        var id = created.body().path("id").asString();

        assertThat(tickets(sato.browser(), "view=" + id)).containsExactly(1L);
        assertThat(view(
                                sato.browser()
                                        .get(tenant.path("/views?withCounts=true"))
                                        .body(),
                                id)
                        .path("count")
                        .asLong())
                .isEqualTo(1);

        // 他のエージェントからは見えない。
        assertThat(suzuki.browser().get(tenant.path("/views")).body().toString())
                .doesNotContain(id);
        assertThat(suzuki.browser().get(tenant.path("/tickets?view=" + id)).status())
                .isEqualTo(404);
        assertThat(suzuki.browser().delete(tenant.path("/views/" + id)).status())
                .isEqualTo(404);

        var renamed = sato.browser()
                .patch(
                        tenant.path("/views/" + id),
                        viewBody("低優先度", Map.of("field", "priority", "op", "in", "value", List.of("low"))));
        assertThat(renamed.status()).isEqualTo(200);
        assertThat(renamed.body().path("name").asString()).isEqualTo("低優先度");
        assertThat(tickets(sato.browser(), "view=" + id)).containsExactly(2L);

        assertThat(sato.browser().delete(tenant.path("/views/" + id)).status()).isEqualTo(204);
        assertThat(sato.browser().get(tenant.path("/tickets?view=" + id)).status())
                .isEqualTo(404);
    }

    @Test
    void onlyAdminsManageSharedViews() {
        var condition = Map.<String, Object>of("field", "group", "op", "in", "value", List.of("@my_groups"));
        assertThat(sato.browser()
                        .post(tenant.path("/views"), viewBody("共有", condition, "shared", true))
                        .status())
                .isEqualTo(404);

        var shared = tenant.admin.post(tenant.path("/views"), viewBody("自分のグループ", condition, "shared", true));
        assertThat(shared.status()).isEqualTo(201);
        assertThat(shared.body().path("kind").asString()).isEqualTo("shared");
        var id = shared.body().path("id").asString();

        // 共有ビューは全エージェントに見え、@my_groups は見る人ごとに決まる。
        assertThat(tickets(sato.browser(), "view=" + id)).containsExactly(1L);
        assertThat(tickets(suzuki.browser(), "view=" + id)).containsExactly(3L);

        assertThat(sato.browser().delete(tenant.path("/views/" + id)).status()).isEqualTo(404);
        assertThat(sato.browser()
                        .patch(tenant.path("/views/" + id), viewBody("変更", condition))
                        .status())
                .isEqualTo(404);
        assertThat(sato.browser().delete(tenant.path("/views/builtin:all")).status())
                .isEqualTo(404);
        assertThat(tenant.admin.delete(tenant.path("/views/" + id)).status()).isEqualTo(204);
    }

    @Test
    void rejectsInvalidConditions() {
        var bad = sato.browser()
                .post(tenant.path("/views"), viewBody("x", Map.of("field", "subject", "op", "eq", "value", "a")));
        assertThat(bad.status()).isEqualTo(422);
        assertThat(bad.code()).isEqualTo("view.invalid_condition");

        var badValue = sato.browser()
                .post(
                        tenant.path("/views"),
                        viewBody("x", Map.of("field", "status", "op", "in", "value", List.of("deleted"))));
        assertThat(badValue.status()).isEqualTo(422);

        assertThat(sato.browser()
                        .post(tenant.path("/views"), Map.of("name", "", "conditions", Map.of("all", List.of())))
                        .status())
                .isEqualTo(400);
    }

    @Test
    void viewsAreStaffOnlyAndTenantScoped() {
        assertThat(requester.browser().get(tenant.path("/views")).status()).isEqualTo(404);

        var id = sato.browser()
                .post(
                        tenant.path("/views"),
                        viewBody("VPN", Map.of("field", "tags", "op", "has_any", "value", List.of("vpn"))))
                .body()
                .path("id")
                .asString();
        var other = new TenantFixture(port, dispatcher, mails);
        assertThat(other.admin.get(other.path("/tickets?view=" + id)).status()).isEqualTo(404);
        assertThat(other.admin.delete(other.path("/views/" + id)).status()).isEqualTo(404);
        assertThat(other.admin
                        .get(other.path("/tickets?view=builtin:all"))
                        .body()
                        .path("total")
                        .asLong())
                .isZero();
    }
}
