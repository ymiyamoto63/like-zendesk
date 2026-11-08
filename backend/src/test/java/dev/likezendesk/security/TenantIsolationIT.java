package dev.likezendesk.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.likezendesk.notification.app.MailDispatcher;
import dev.likezendesk.support.ApiClient;
import dev.likezendesk.support.MailTestConfiguration;
import dev.likezendesk.support.PostgresTestConfiguration;
import dev.likezendesk.support.RecordingMailSender;
import dev.likezendesk.support.TenantFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * 全エンドポイントのテナント分離（architecture.md 2.3 の 2、受け入れ基準 10・11）。
 *
 * <p>エンドポイントの一覧は実行中の OpenAPI 定義から取るので、追加したエンドポイントは自動で対象に入る。パスの変数に当てる値がないと失敗するので、
 * 新しい種類のリソースを足したら {@link #resourceOf} に加える。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestConfiguration.class, MailTestConfiguration.class})
class TenantIsolationIT {

    private static final Pattern VARIABLE = Pattern.compile("/([a-z-]+)/\\{([a-zA-Z]+)}");

    /** ログインしていなくても呼べる（テナント内のデータを返さない）エンドポイント。 */
    private static final Set<String> ANONYMOUS = Set.of(
            "GET /api/t/{slug}",
            "POST /api/t/{slug}/session",
            "DELETE /api/t/{slug}/session",
            "POST /api/t/{slug}/requesters",
            "POST /api/t/{slug}/email-verifications",
            "POST /api/t/{slug}/password-resets",
            "PUT /api/t/{slug}/password-resets/{token}",
            "GET /api/t/{slug}/invitations/{token}",
            "POST /api/t/{slug}/invitations/{token}/accept");

    /** 本文の検証（400）より先に、リソースの検索（404）まで届くための最小の本文。ない操作は {@code {}} を送る。 */
    private static final Map<String, Map<String, Object>> BODIES = Map.of(
            "PATCH /api/t/{slug}/admin/groups/{id}", Map.of("name", "乗っ取り"),
            "PUT /api/t/{slug}/admin/groups/{id}/members", Map.of("userIds", List.of()),
            "PATCH /api/t/{slug}/admin/organizations/{id}", Map.of("name", "乗っ取り"),
            "PATCH /api/t/{slug}/admin/users/{id}", Map.of("name", "乗っ取り"),
            "POST /api/t/{slug}/invitations/{token}/accept", Map.of("name", "乗っ取り", "password", TenantFixture.PASSWORD),
            "PUT /api/t/{slug}/password-resets/{token}", Map.of("password", TenantFixture.PASSWORD),
            "PATCH /api/t/{slug}/tickets/{number}", Map.of("version", 0, "priority", "high"),
            "POST /api/t/{slug}/tickets/{number}/comments", Map.of("body", "乗っ取り", "public", true),
            "POST /api/t/{slug}/portal/tickets/{number}/comments", Map.of("body", "乗っ取り"),
            "PATCH /api/t/{slug}/views/{id}",
                    Map.of(
                            "name",
                            "乗っ取り",
                            "conditions",
                            Map.of("all", List.of(Map.of("field", "status", "op", "in", "value", List.of("new"))))));

    @LocalServerPort
    int port;

    @Autowired
    MailDispatcher dispatcher;

    @Autowired
    RecordingMailSender mails;

    TenantFixture a;
    TenantFixture b;
    Map<String, String> resourcesOfA;
    TenantFixture.User requesterOfA;
    TenantFixture.User otherRequesterOfA;

    record Operation(String method, String path) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    @BeforeEach
    void setUp() {
        a = new TenantFixture(port, dispatcher, mails);
        b = new TenantFixture(port, dispatcher, mails);
        var agent = a.agent("佐藤 一郎");
        requesterOfA = a.requester("山田 花子");
        otherRequesterOfA = a.requester("田中 一樹");
        var group = a.group("インフラ", agent);

        // 番号はテナントごとに 1 から振るので、B に同じ番号のチケットがないよう A に 3 件作り、3 番を使う
        for (int i = 0; i < 3; i++) {
            assertThat(requesterOfA
                            .browser()
                            .post(a.path("/portal/tickets"), Map.of("subject", "VPN " + i, "body", "つながらない"))
                            .status())
                    .isEqualTo(201);
        }
        var attachment =
                requesterOfA.browser().upload(a.path("/attachments"), "log.txt", "text/plain", "error 809".getBytes());
        assertThat(attachment.status()).isEqualTo(201);
        var organization = a.admin.post(a.path("/admin/organizations"), Map.of("name", "営業部"));
        var view = a.admin.post(
                a.path("/views"),
                Map.of(
                        "name",
                        "共有ビュー",
                        "shared",
                        true,
                        "conditions",
                        Map.of("all", List.of(Map.of("field", "status", "op", "in", "value", List.of("new"))))));
        assertThat(view.status()).isEqualTo(201);
        var invited = "invitee@" + a.slug + ".test";
        assertThat(a.admin
                        .post(a.path("/admin/invitations"), Map.of("email", invited, "role", "agent"))
                        .status())
                .isEqualTo(202);
        dispatcher.dispatchDue();

        resourcesOfA = Map.of(
                "groups", group.toString(),
                "organizations", organization.body().path("id").asString(),
                "users", requesterOfA.id().toString(),
                "attachments", attachment.body().path("id").asString(),
                "views", view.body().path("id").asString(),
                "invitations", mails.tokenSentTo(invited),
                "password-resets", mails.tokenSentTo(invited),
                "tickets", "3");
    }

    @Test
    void otherTenantCannotReachResourcesByTheirIds() {
        // B の管理者が、B のテナントのパスで A のリソースの ID を指定する
        var soft = new SoftAssertions();
        int checked = 0;
        for (var op : operations()) {
            if (!VARIABLE.matcher(op.path().replace("/t/{slug}", "")).find()) {
                continue;
            }
            var response = call(b.admin, op, b.slug);
            soft.assertThat(response.status()).as("%s", op).isEqualTo(404);
            checked++;
        }
        soft.assertAll();
        assertThat(checked).as("ID を取るエンドポイントの数").isGreaterThan(15);
        // 変更が届いていない
        assertThat(requesterOfA
                        .browser()
                        .get(a.path("/portal/tickets/3"))
                        .body()
                        .path("subject")
                        .asString())
                .isEqualTo("VPN 2");
    }

    @Test
    void sessionOfOtherTenantIsNotAccepted() {
        // B の管理者のセッションで、A のテナントのパスを呼ぶ
        var soft = new SoftAssertions();
        for (var op : operations()) {
            if (ANONYMOUS.contains(op.toString())) {
                continue;
            }
            var response = call(b.admin, op, a.slug);
            soft.assertThat(response.status()).as("%s", op).isEqualTo(401);
        }
        soft.assertAll();
    }

    @Test
    void otherRequesterCannotReachTicketsOrStaffApi() {
        var soft = new SoftAssertions();
        for (var op : operations()) {
            if (ANONYMOUS.contains(op.toString())
                    || op.path().equals("/api/t/{slug}/me")
                    || op.path().equals("/api/t/{slug}/attachments")
                            && op.method().equals("POST")
                    || op.path().matches("/api/t/\\{slug}/portal/tickets")) {
                continue;
            }
            var response = call(otherRequesterOfA.browser(), op, a.slug);
            soft.assertThat(response.status()).as("%s", op).isEqualTo(404);
        }
        soft.assertAll();
    }

    private List<Operation> operations() {
        var spec = new ApiClient(port).get("/api/docs").body().path("paths");
        var result = new ArrayList<Operation>();
        for (var path : spec.propertyNames()) {
            if (!path.startsWith("/api/t/{slug}")) {
                continue;
            }
            for (var method : spec.path(path).propertyNames()) {
                result.add(new Operation(method.toUpperCase(Locale.ROOT), path));
            }
        }
        assertThat(result).isNotEmpty();
        return result;
    }

    private ApiClient.Response call(ApiClient client, Operation op, String slug) {
        var path = resolve(op.path()).replace("{slug}", slug);
        var body = BODIES.getOrDefault(op.toString(), Map.of());
        if (op.toString().equals("POST /api/t/{slug}/attachments")) {
            return client.upload(path, "log.txt", "text/plain", "error 809".getBytes());
        }
        return switch (op.method()) {
            case "GET" -> client.get(path);
            case "POST" -> client.post(path, body);
            case "PUT" -> client.put(path, body);
            case "PATCH" -> client.patch(path, body);
            case "DELETE" -> client.delete(path);
            default -> throw new AssertionError("Unknown method " + op);
        };
    }

    /** {@code /groups/{id}} の {@code {id}} を、A のグループの ID にする。 */
    private String resolve(String path) {
        Matcher m = VARIABLE.matcher(path);
        var out = new StringBuilder();
        while (m.find()) {
            if (m.group(2).equals("slug")) {
                m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
                continue;
            }
            m.appendReplacement(out, "/" + m.group(1) + "/" + Matcher.quoteReplacement(resourceOf(m.group(1), path)));
        }
        m.appendTail(out);
        return out.toString();
    }

    private String resourceOf(String collection, String path) {
        var value = resourcesOfA.get(collection);
        if (value == null) {
            throw new AssertionError(
                    "No resource of tenant A for /" + collection + " in " + path + ". Add one in setUp().");
        }
        return value;
    }
}
