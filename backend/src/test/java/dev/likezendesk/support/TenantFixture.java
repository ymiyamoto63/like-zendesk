package dev.likezendesk.support;

import static org.assertj.core.api.Assertions.assertThat;

import dev.likezendesk.notification.app.MailDispatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 結合テスト用に、テナントとユーザーを API だけで用意する。テストごとに別のテナントを作り、DB の後片付けを要らなくする。
 *
 * <p>{@link MailTestConfiguration} を読み込んだテストで使う（招待とメール確認のリンクをメールから取り出すため）。
 */
public final class TenantFixture {

    public static final String PASSWORD = "correct-horse";
    private static final AtomicInteger SEQ = new AtomicInteger();

    private final int port;
    private final MailDispatcher dispatcher;
    private final RecordingMailSender mails;
    public final String slug;
    public final ApiClient admin;

    public record User(UUID id, String email, ApiClient browser) {}

    public TenantFixture(int port, MailDispatcher dispatcher, RecordingMailSender mails) {
        this.port = port;
        this.dispatcher = dispatcher;
        this.mails = mails;
        this.slug = unique("t");
        this.admin = new ApiClient(port);
        var created = admin.post(
                "/api/tenants",
                Map.of(
                        "tenantName",
                        "テナント " + slug,
                        "slug",
                        slug,
                        "adminName",
                        "管理者",
                        "email",
                        "admin@" + slug + ".test",
                        "password",
                        PASSWORD));
        assertThat(created.status()).isEqualTo(201);
    }

    public static String unique(String prefix) {
        return prefix + System.nanoTime() % 1_000_000_000 + "-" + SEQ.incrementAndGet();
    }

    /** {@code /api/t/{slug}} に続くパス。 */
    public String path(String rest) {
        return "/api/t/" + slug + rest;
    }

    public ApiClient browser() {
        return new ApiClient(port);
    }

    /** エージェントを招待し、受諾してログインした状態で返す。 */
    public User agent(String name) {
        var email = unique("agent") + "@" + slug + ".test";
        assertThat(admin.post(path("/admin/invitations"), Map.of("email", email, "role", "agent"))
                        .status())
                .isEqualTo(202);
        dispatcher.dispatchDue();
        var browser = browser();
        var me = browser.post(
                path("/invitations/" + mails.tokenSentTo(email) + "/accept"),
                Map.of("name", name, "password", PASSWORD));
        assertThat(me.status()).isEqualTo(200);
        return new User(UUID.fromString(me.body().path("id").asString()), email, browser);
    }

    /** 依頼者を登録し、メールアドレスを確認してログインした状態で返す。 */
    public User requester(String name) {
        var email = unique("req") + "@customer.test";
        assertThat(browser()
                        .post(path("/requesters"), Map.of("name", name, "email", email, "password", PASSWORD))
                        .status())
                .isEqualTo(202);
        dispatcher.dispatchDue();
        var browser = browser();
        var me = browser.post(path("/email-verifications"), Map.of("token", mails.tokenSentTo(email)));
        assertThat(me.status()).isEqualTo(200);
        return new User(UUID.fromString(me.body().path("id").asString()), email, browser);
    }

    /** グループを作り、メンバーを設定する。 */
    public UUID group(String name, User... members) {
        var created = admin.post(path("/admin/groups"), Map.of("name", name));
        assertThat(created.status()).isEqualTo(201);
        var id = UUID.fromString(created.body().path("id").asString());
        var ids = java.util.Arrays.stream(members).map(m -> m.id().toString()).toList();
        assertThat(admin.put(path("/admin/groups/" + id + "/members"), Map.of("userIds", List.copyOf(ids)))
                        .status())
                .isEqualTo(204);
        return id;
    }
}
