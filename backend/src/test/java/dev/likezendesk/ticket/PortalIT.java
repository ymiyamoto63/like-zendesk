package dev.likezendesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.likezendesk.notification.app.MailDispatcher;
import dev.likezendesk.support.ApiClient;
import dev.likezendesk.support.MailTestConfiguration;
import dev.likezendesk.support.PostgresTestConfiguration;
import dev.likezendesk.support.RecordingMailSender;
import dev.likezendesk.support.TenantFixture;
import dev.likezendesk.ticket.app.AutoClose;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

/** 依頼者ポータル（F-P-1〜4、受け入れ基準 3〜7）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestConfiguration.class, MailTestConfiguration.class})
class PortalIT {

    @LocalServerPort
    int port;

    @Autowired
    MailDispatcher dispatcher;

    @Autowired
    RecordingMailSender mails;

    @Autowired
    AutoClose autoClose;

    @Autowired
    JdbcClient jdbc;

    TenantFixture tenant;
    TenantFixture.User sato;
    TenantFixture.User hanako;
    UUID infra;

    @BeforeEach
    void setUp() {
        tenant = new TenantFixture(port, dispatcher, mails);
        sato = tenant.agent("佐藤 一郎");
        hanako = tenant.requester("山田 花子");
        infra = tenant.group("インフラ", sato);
    }

    private long submit(ApiClient requester, String subject) {
        var created = requester.post(tenant.path("/portal/tickets"), Map.of("subject", subject, "body", "朝から接続できません。"));
        assertThat(created.status()).isEqualTo(201);
        return created.body().path("number").asLong();
    }

    private int version(long number) {
        return sato.browser()
                .get(tenant.path("/tickets/" + number))
                .body()
                .path("version")
                .asInt();
    }

    private List<String> bodies(ApiClient requester, long number) {
        var comments = requester.get(tenant.path("/portal/tickets/" + number + "/comments"));
        assertThat(comments.status()).isEqualTo(200);
        return StreamSupport.stream(comments.body().path("items").spliterator(), false)
                .map(c -> c.path("body").asString())
                .toList();
    }

    @Test
    void requestersSubmitAndSeeOnlyTheirOwnPublicConversation() {
        var before = mails.countTo(hanako.email());
        var number = submit(hanako.browser(), "VPN に接続できない");
        dispatcher.dispatchDue();
        assertThat(mails.countTo(hanako.email())).isEqualTo(before + 1);
        assertThat(mails.lastTo(hanako.email()).getSubject()).contains("受け付けました");

        var ticket =
                hanako.browser().get(tenant.path("/portal/tickets/" + number)).body();
        assertThat(ticket.path("status").asString()).isEqualTo("new");
        assertThat(ticket.path("assigneeName").isNull()).isTrue();
        // 内部項目は返さない
        assertThat(ticket.has("group")).isFalse();
        assertThat(ticket.has("tags")).isFalse();
        assertThat(ticket.has("priority")).isFalse();

        // エージェント側では依頼者の起票として見える
        var staffView = sato.browser().get(tenant.path("/tickets/" + number)).body();
        assertThat(staffView.path("requester").path("name").asString()).isEqualTo("山田 花子");
        assertThat(staffView.path("priority").asString()).isEqualTo("normal");

        sato.browser()
                .post(tenant.path("/tickets/" + number + "/comments"), Map.of("body", "社内メモ: 調査中", "public", false));
        sato.browser()
                .post(tenant.path("/tickets/" + number + "/comments"), Map.of("body", "再起動をお試しください", "public", true));
        assertThat(bodies(hanako.browser(), number)).containsExactly("朝から接続できません。", "再起動をお試しください");
        var first = hanako.browser()
                .get(tenant.path("/portal/tickets/" + number + "/comments"))
                .body()
                .path("items");
        assertThat(first.get(0).path("mine").asBoolean()).isTrue();
        assertThat(first.get(1).path("mine").asBoolean()).isFalse();
        assertThat(first.get(1).path("authorName").asString()).isEqualTo("佐藤 一郎");

        // 他の依頼者には見えない
        var other = tenant.requester("鈴木 三郎");
        submit(other.browser(), "別件");
        assertThat(other.browser().get(tenant.path("/portal/tickets/" + number)).status())
                .isEqualTo(404);
        assertThat(other.browser()
                        .get(tenant.path("/portal/tickets/" + number + "/comments"))
                        .status())
                .isEqualTo(404);
        assertThat(other.browser()
                        .post(tenant.path("/portal/tickets/" + number + "/comments"), Map.of("body", "x"))
                        .status())
                .isEqualTo(404);
        var list = hanako.browser().get(tenant.path("/portal/tickets")).body();
        assertThat(list.path("total").asLong()).isEqualTo(1);
        assertThat(list.path("items").get(0).path("number").asLong()).isEqualTo(number);

        // スタッフはポータル API を使わない
        assertThat(sato.browser().get(tenant.path("/portal/tickets")).status()).isEqualTo(404);
    }

    @Test
    void replyingReopensPendingTicketsAndNotifiesTheAssignee() {
        var number = submit(hanako.browser(), "VPN に接続できない");
        var pending = sato.browser()
                .post(
                        tenant.path("/tickets/" + number + "/comments"),
                        Map.of(
                                "body",
                                "ログをください",
                                "public",
                                true,
                                "ticket",
                                Map.of(
                                        "version",
                                        0,
                                        "status",
                                        "pending",
                                        "groupId",
                                        infra.toString(),
                                        "assigneeId",
                                        sato.id().toString())));
        assertThat(pending.status()).isEqualTo(201);
        assertThat(hanako.browser()
                        .get(tenant.path("/portal/tickets/" + number))
                        .body()
                        .path("assigneeName")
                        .asString())
                .isEqualTo("佐藤 一郎");

        dispatcher.dispatchDue();
        var satoBefore = mails.countTo(sato.email());
        var reply =
                hanako.browser().post(tenant.path("/portal/tickets/" + number + "/comments"), Map.of("body", "ログです"));
        assertThat(reply.status()).isEqualTo(201);
        assertThat(reply.body().path("followUp").asBoolean()).isFalse();
        assertThat(reply.body().path("ticket").path("status").asString()).isEqualTo("open");
        assertThat(reply.body().path("comment").path("mine").asBoolean()).isTrue();

        dispatcher.dispatchDue();
        assertThat(mails.countTo(sato.email())).isEqualTo(satoBefore + 1);
        var mail = mails.lastTo(sato.email());
        assertThat(mail.getSubject()).contains("返信がありました");
        assertThat(mail.getText()).contains("山田 花子", "ログです", "/agent/tickets/" + number);

        // 監査ログの実行者は依頼者
        var events = sato.browser()
                .get(tenant.path("/tickets/" + number + "/events"))
                .body()
                .path("items");
        JsonNode last = StreamSupport.stream(events.spliterator(), false)
                .reduce((a, b) -> b)
                .orElseThrow();
        assertThat(last.path("actor").path("name").asString()).isEqualTo("山田 花子");
    }

    @Test
    void requestersSolveAndReopenTheirTickets() {
        var unassigned = submit(hanako.browser(), "未割り当て");
        var refused = hanako.browser().post(tenant.path("/portal/tickets/" + unassigned + "/solve"), Map.of());
        assertThat(refused.status()).isEqualTo(422);
        assertThat(refused.code()).isEqualTo("status.solved_requires_assignee");

        var number = submit(hanako.browser(), "VPN に接続できない");
        sato.browser()
                .patch(
                        tenant.path("/tickets/" + number),
                        Map.of(
                                "version",
                                0,
                                "groupId",
                                infra.toString(),
                                "assigneeId",
                                sato.id().toString()));
        var solved = hanako.browser().post(tenant.path("/portal/tickets/" + number + "/solve"), Map.of());
        assertThat(solved.status()).isEqualTo(200);
        assertThat(solved.body().path("status").asString()).isEqualTo("solved");

        var reply =
                hanako.browser().post(tenant.path("/portal/tickets/" + number + "/comments"), Map.of("body", "再発しました"));
        assertThat(reply.body().path("ticket").path("status").asString()).isEqualTo("open");
        assertThat(version(number)).isEqualTo(3);
    }

    @Test
    void replyingToAClosedTicketOpensAFollowUp() {
        var number = submit(hanako.browser(), "VPN に接続できない");
        sato.browser()
                .patch(
                        tenant.path("/tickets/" + number),
                        Map.of(
                                "version",
                                0,
                                "status",
                                "solved",
                                "groupId",
                                infra.toString(),
                                "assigneeId",
                                sato.id().toString()));
        jdbc.sql("""
                        UPDATE tickets SET solved_at = :at
                        WHERE number = :number AND tenant_id = (SELECT id FROM tenants WHERE slug = :slug)
                        """)
                .param("at", Timestamp.from(Instant.now().minus(5, ChronoUnit.DAYS)))
                .param("number", number)
                .param("slug", tenant.slug)
                .update();
        autoClose.closeDue();
        assertThat(hanako.browser()
                        .get(tenant.path("/portal/tickets/" + number))
                        .body()
                        .path("status")
                        .asString())
                .isEqualTo("closed");

        var reply = hanako.browser()
                .post(tenant.path("/portal/tickets/" + number + "/comments"), Map.of("body", "また切れました"));
        assertThat(reply.status()).isEqualTo(201);
        assertThat(reply.body().path("followUp").asBoolean()).isTrue();
        var followUp = reply.body().path("ticket");
        assertThat(followUp.path("number").asLong()).isNotEqualTo(number);
        assertThat(followUp.path("followUpOf").asLong()).isEqualTo(number);
        assertThat(followUp.path("subject").asString()).isEqualTo("VPN に接続できない");
        assertThat(followUp.path("status").asString()).isEqualTo("new");
        assertThat(bodies(hanako.browser(), followUp.path("number").asLong())).containsExactly("また切れました");

        // 元のチケットは変わらない
        assertThat(bodies(hanako.browser(), number)).doesNotContain("また切れました");
        assertThat(sato.browser()
                        .get(tenant.path("/tickets/" + followUp.path("number").asLong()))
                        .body()
                        .path("followUpOf")
                        .asLong())
                .isEqualTo(number);
        assertThat(hanako.browser()
                        .post(tenant.path("/portal/tickets/" + number + "/solve"), Map.of())
                        .code())
                .isEqualTo("status.closed_is_final");
    }
}
