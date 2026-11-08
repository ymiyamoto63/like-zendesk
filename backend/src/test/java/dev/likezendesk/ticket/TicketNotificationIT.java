package dev.likezendesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.likezendesk.notification.app.MailDispatcher;
import dev.likezendesk.support.MailTestConfiguration;
import dev.likezendesk.support.PostgresTestConfiguration;
import dev.likezendesk.support.RecordingMailSender;
import dev.likezendesk.support.TenantFixture;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/** チケットのメール通知（F-N-1〜3）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestConfiguration.class, MailTestConfiguration.class})
class TicketNotificationIT {

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
        infra = tenant.group("インフラ", sato, suzuki);
    }

    private long create(Map<String, Object> extra) {
        var body = new HashMap<String, Object>(
                Map.of("requesterId", requester.id().toString(), "subject", "VPN に接続できない", "body", "朝から接続できません。"));
        body.putAll(extra);
        var created = sato.browser().post(tenant.path("/tickets"), body);
        assertThat(created.status()).isEqualTo(201);
        dispatcher.dispatchDue();
        return created.body().path("number").asLong();
    }

    private void comment(long number, String body, boolean isPublic) {
        assertThat(sato.browser()
                        .post(tenant.path("/tickets/" + number + "/comments"), Map.of("body", body, "public", isPublic))
                        .status())
                .isEqualTo(201);
        dispatcher.dispatchDue();
    }

    @Test
    void requesterReceivesAnAcknowledgement() {
        var before = mails.countTo(requester.email());
        var number = create(Map.of());

        assertThat(mails.countTo(requester.email())).isEqualTo(before + 1);
        var mail = mails.lastTo(requester.email());
        assertThat(mail.getSubject()).contains("#" + number, "VPN に接続できない", "受け付けました");
        assertThat(mail.getText()).contains("朝から接続できません。").contains("/t/" + tenant.slug + "/requests/" + number);
    }

    @Test
    void publicRepliesGoToTheRequesterButInternalNotesDoNot() {
        var number = create(Map.of());
        var before = mails.countTo(requester.email());

        comment(number, "社内メモ: ベンダーに確認中", false);
        assertThat(mails.countTo(requester.email())).isEqualTo(before);

        comment(number, "ログを添付してください。", true);
        assertThat(mails.countTo(requester.email())).isEqualTo(before + 1);
        var mail = mails.lastTo(requester.email());
        assertThat(mail.getSubject()).contains("返信がありました");
        assertThat(mail.getText()).contains("佐藤 一郎", "ログを添付してください。").doesNotContain("ベンダー");
    }

    @Test
    void newAssigneesAreNotifiedButNotForTheirOwnChanges() {
        var suzukiBefore = mails.countTo(suzuki.email());
        var satoBefore = mails.countTo(sato.email());
        var number = create(
                Map.of("groupId", infra.toString(), "assigneeId", suzuki.id().toString()));
        assertThat(mails.countTo(suzuki.email())).isEqualTo(suzukiBefore + 1);
        var mail = mails.lastTo(suzuki.email());
        assertThat(mail.getSubject()).contains("担当になりました");
        assertThat(mail.getText()).contains("佐藤 一郎").contains("/t/" + tenant.slug + "/agent/tickets/" + number);

        // 担当者が変わらない更新では送らない
        assertThat(sato.browser()
                        .patch(tenant.path("/tickets/" + number), Map.of("version", 0, "priority", "high"))
                        .status())
                .isEqualTo(200);
        // 自分を担当者にしても自分には送らない
        assertThat(sato.browser()
                        .patch(
                                tenant.path("/tickets/" + number),
                                Map.of("version", 1, "assigneeId", sato.id().toString()))
                        .status())
                .isEqualTo(200);
        dispatcher.dispatchDue();
        assertThat(mails.countTo(suzuki.email())).isEqualTo(suzukiBefore + 1);
        assertThat(mails.countTo(sato.email())).isEqualTo(satoBefore);

        // コメントと同時の割り当ても知らせる
        assertThat(sato.browser()
                        .post(
                                tenant.path("/tickets/" + number + "/comments"),
                                Map.of(
                                        "body",
                                        "鈴木さん、お願いします",
                                        "public",
                                        false,
                                        "ticket",
                                        Map.of(
                                                "version",
                                                2,
                                                "assigneeId",
                                                suzuki.id().toString())))
                        .status())
                .isEqualTo(201);
        dispatcher.dispatchDue();
        assertThat(mails.countTo(suzuki.email())).isEqualTo(suzukiBefore + 2);
    }
}
