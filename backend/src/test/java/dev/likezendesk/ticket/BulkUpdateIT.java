package dev.likezendesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.likezendesk.notification.app.MailDispatcher;
import dev.likezendesk.support.MailTestConfiguration;
import dev.likezendesk.support.PostgresTestConfiguration;
import dev.likezendesk.support.RecordingMailSender;
import dev.likezendesk.support.TenantFixture;
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

/** 複数チケットの一括更新（F-T-8）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestConfiguration.class, MailTestConfiguration.class})
class BulkUpdateIT {

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
        for (var tags : List.of(List.of("vpn"), List.of("mail", "old"), List.<String>of())) {
            assertThat(sato.browser()
                            .post(
                                    tenant.path("/tickets"),
                                    Map.of(
                                            "requesterId",
                                            requester.id().toString(),
                                            "subject",
                                            "問い合わせ",
                                            "body",
                                            "本文",
                                            "tags",
                                            tags))
                            .status())
                    .isEqualTo(201);
        }
    }

    private JsonNode ticket(long number) {
        return sato.browser().get(tenant.path("/tickets/" + number)).body();
    }

    private static List<String> strings(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::asString)
                .toList();
    }

    @Test
    void assignsAndTagsSeveralTicketsAtOnce() {
        var before = mails.countTo(suzuki.email());
        var response = sato.browser()
                .patch(
                        tenant.path("/tickets"),
                        Map.of(
                                "numbers", List.of(1, 2, 3),
                                "status", "open",
                                "groupId", infra.toString(),
                                "assigneeId", suzuki.id().toString(),
                                "addTags", List.of("Urgent"),
                                "removeTags", List.of("old")));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().path("updated").size()).isEqualTo(3);
        assertThat(response.body().path("failed").size()).isZero();

        assertThat(ticket(1).path("assignee").path("name").asString()).isEqualTo("鈴木 次郎");
        assertThat(ticket(1).path("status").asString()).isEqualTo("open");
        assertThat(strings(ticket(1).path("tags"))).containsExactlyInAnyOrder("vpn", "urgent");
        assertThat(strings(ticket(2).path("tags"))).containsExactlyInAnyOrder("mail", "urgent");
        assertThat(strings(ticket(3).path("tags"))).containsExactly("urgent");

        // 1 件ずつの更新と同じく、監査ログと担当者への通知が残る
        var events = sato.browser().get(tenant.path("/tickets/2/events")).body().path("items");
        assertThat(events.toString()).contains("assignee", "tags");
        dispatcher.dispatchDue();
        assertThat(mails.countTo(suzuki.email())).isEqualTo(before + 3);
    }

    @Test
    void reportsTicketsThatBreakTheRules() {
        // 2 だけ担当者つきにしておき、解決済みにできるのは 2 だけ
        assertThat(sato.browser()
                        .patch(
                                tenant.path("/tickets/2"),
                                Map.of(
                                        "version",
                                        0,
                                        "groupId",
                                        infra.toString(),
                                        "assigneeId",
                                        sato.id().toString()))
                        .status())
                .isEqualTo(200);
        var response = sato.browser()
                .patch(tenant.path("/tickets"), Map.of("numbers", List.of(1, 2, 999), "status", "solved"));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().path("updated").get(0).path("number").asLong())
                .isEqualTo(2);
        var failed = response.body().path("failed");
        assertThat(failed.size()).isEqualTo(2);
        assertThat(failed.get(0).path("number").asLong()).isEqualTo(1);
        assertThat(failed.get(0).path("code").asString()).isNotBlank();
        assertThat(failed.get(1).path("code").asString()).isEqualTo("not_found");
        assertThat(ticket(1).path("status").asString()).isEqualTo("new");
    }

    @Test
    void clearsTheAssigneeWithNull() {
        sato.browser()
                .patch(
                        tenant.path("/tickets"),
                        Map.of(
                                "numbers",
                                List.of(1),
                                "groupId",
                                infra.toString(),
                                "assigneeId",
                                sato.id().toString()));
        var body = new HashMap<String, Object>();
        body.put("numbers", List.of(1));
        body.put("assigneeId", null);
        assertThat(sato.browser().patch(tenant.path("/tickets"), body).status()).isEqualTo(200);
        assertThat(ticket(1).path("assignee").isNull()).isTrue();
        assertThat(ticket(1).path("group").path("name").asString()).isEqualTo("インフラ");
    }

    @Test
    void isStaffOnlyAndLimited() {
        assertThat(requester
                        .browser()
                        .patch(tenant.path("/tickets"), Map.of("numbers", List.of(1), "status", "open"))
                        .status())
                .isEqualTo(404);
        assertThat(sato.browser()
                        .patch(tenant.path("/tickets"), Map.of("numbers", List.of(), "status", "open"))
                        .status())
                .isEqualTo(400);
        var other = new TenantFixture(port, dispatcher, mails);
        var response = other.admin.patch(other.path("/tickets"), Map.of("numbers", List.of(1), "status", "open"));
        assertThat(response.body().path("failed").get(0).path("code").asString())
                .isEqualTo("not_found");
        assertThat(ticket(1).path("status").asString()).isEqualTo("new");
    }
}
