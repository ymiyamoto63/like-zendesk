package dev.likezendesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.likezendesk.notification.app.MailDispatcher;
import dev.likezendesk.support.ApiClient;
import dev.likezendesk.support.MailTestConfiguration;
import dev.likezendesk.support.PostgresTestConfiguration;
import dev.likezendesk.support.RecordingMailSender;
import dev.likezendesk.support.TenantFixture;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;

/** チケットの作成・更新・コメント・監査ログ（F-T、F-C、F-L）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestConfiguration.class, MailTestConfiguration.class})
class TicketIT {

    @LocalServerPort
    int port;

    @Autowired
    MailDispatcher dispatcher;

    @Autowired
    RecordingMailSender mails;

    TenantFixture tenant;
    TenantFixture.User sato;
    TenantFixture.User requester;
    UUID infra;

    @BeforeEach
    void setUp() {
        tenant = new TenantFixture(port, dispatcher, mails);
        sato = tenant.agent("佐藤 一郎");
        requester = tenant.requester("山田 花子");
        infra = tenant.group("インフラ", sato);
    }

    private JsonNode create(ApiClient browser, Map<String, Object> extra) {
        var body = new HashMap<String, Object>(
                Map.of("requesterId", requester.id().toString(), "subject", "VPN に接続できない", "body", "朝から接続できません。"));
        body.putAll(extra);
        var created = browser.post(tenant.path("/tickets"), body);
        assertThat(created.status()).isEqualTo(201);
        return created.body();
    }

    private ApiClient.Response patch(long number, Map<String, Object> body) {
        return sato.browser().patch(tenant.path("/tickets/" + number), body);
    }

    private static Map<String, Object> with(Object... keyValues) {
        var map = new HashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    void agentCreatesATicketForARequester() {
        var ticket = create(sato.browser(), Map.of("priority", "high", "type", "incident", "tags", List.of("VPN")));

        assertThat(ticket.path("number").asLong()).isEqualTo(1);
        assertThat(ticket.path("status").asString()).isEqualTo("new");
        assertThat(ticket.path("priority").asString()).isEqualTo("high");
        assertThat(ticket.path("requester").path("name").asString()).isEqualTo("山田 花子");
        assertThat(ticket.path("assignee").isNull()).isTrue();
        assertThat(ticket.path("tags").get(0).asString()).isEqualTo("vpn");
        assertThat(create(sato.browser(), Map.of()).path("number").asLong())
                .as("numbers are sequential")
                .isEqualTo(2);

        var comments = sato.browser().get(tenant.path("/tickets/1/comments")).body();
        assertThat(comments.get(0).path("body").asString()).isEqualTo("朝から接続できません。");
        assertThat(comments.get(0).path("public").asBoolean()).isTrue();

        var list = sato.browser().get(tenant.path("/tickets")).body();
        assertThat(list.path("total").asLong()).isEqualTo(2);
        assertThat(list.path("items").get(0).path("number").asLong())
                .as("newest first")
                .isEqualTo(2);
    }

    @Test
    void assigningOpensTheTicketAndOmittedFieldsStayUnchanged() {
        var number = create(sato.browser(), Map.of("tags", List.of("vpn")))
                .path("number")
                .asLong();

        var assigned = patch(
                number,
                with(
                        "version",
                        0,
                        "groupId",
                        infra.toString(),
                        "assigneeId",
                        sato.id().toString()));
        assertThat(assigned.status()).isEqualTo(200);
        assertThat(assigned.body().path("status").asString()).isEqualTo("open");
        assertThat(assigned.body().path("assignee").path("name").asString()).isEqualTo("佐藤 一郎");
        assertThat(assigned.body().path("tags").get(0).asString()).isEqualTo("vpn");
        assertThat(assigned.body().path("version").asInt()).isEqualTo(1);

        var pending = patch(number, with("version", 1, "status", "pending"));
        assertThat(pending.body().path("assignee").path("name").asString())
                .as("omitted assignee is kept")
                .isEqualTo("佐藤 一郎");

        var unassigned = patch(number, with("version", 2, "assigneeId", null));
        assertThat(unassigned.status()).isEqualTo(200);
        assertThat(unassigned.body().path("assignee").isNull())
                .as("explicit null removes the assignee")
                .isTrue();
        assertThat(unassigned.body().path("group").path("name").asString()).isEqualTo("インフラ");
    }

    @Test
    void staleVersionIsAConflict() {
        var number = create(sato.browser(), Map.of()).path("number").asLong();
        patch(number, with("version", 0, "priority", "high"));

        var stale = patch(number, with("version", 0, "priority", "low"));
        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.code()).isEqualTo("ticket.version_conflict");
        assertThat(stale.body().path("currentVersion").asInt()).isEqualTo(1);
        assertThat(sato.browser()
                        .get(tenant.path("/tickets/" + number))
                        .body()
                        .path("priority")
                        .asString())
                .isEqualTo("high");
    }

    @Test
    void transitionRulesAreEnforced() {
        var number = create(sato.browser(), Map.of()).path("number").asLong();

        var solvedWithoutAssignee = patch(number, with("version", 0, "status", "solved"));
        assertThat(solvedWithoutAssignee.status()).isEqualTo(422);
        assertThat(solvedWithoutAssignee.code()).isEqualTo("status.solved_requires_assignee");

        var closed = patch(number, with("version", 0, "status", "closed"));
        assertThat(closed.code()).isEqualTo("status.closed_by_system_only");

        var other = tenant.agent("鈴木");
        var notMember = patch(
                number,
                with(
                        "version",
                        0,
                        "groupId",
                        infra.toString(),
                        "assigneeId",
                        other.id().toString()));
        assertThat(notMember.status()).isEqualTo(422);
        assertThat(notMember.code()).isEqualTo("assignee.not_in_group");

        var bad = patch(number, with("version", 0, "status", "archived"));
        assertThat(bad.status()).isEqualTo(400);
    }

    @Test
    void replyAndStatusChangeAreOneChangeInTheAuditLog() {
        var number = create(
                        sato.browser(),
                        Map.of(
                                "groupId",
                                infra.toString(),
                                "assigneeId",
                                sato.id().toString()))
                .path("number")
                .asLong();

        var reply = sato.browser()
                .post(
                        tenant.path("/tickets/" + number + "/comments"),
                        Map.of(
                                "body",
                                "ログを添付してください。",
                                "public",
                                true,
                                "ticket",
                                Map.of("version", 0, "status", "pending")));
        assertThat(reply.status()).isEqualTo(201);
        assertThat(reply.body().path("ticket").path("status").asString()).isEqualTo("pending");
        assertThat(reply.body().path("comment").path("author").path("name").asString())
                .isEqualTo("佐藤 一郎");

        var note = sato.browser()
                .post(tenant.path("/tickets/" + number + "/comments"), Map.of("body", "ベンダーに確認中", "public", false));
        assertThat(note.status()).isEqualTo(201);
        assertThat(note.body().path("ticket").path("version").asInt())
                .as("a comment bumps the version")
                .isEqualTo(2);

        var events = sato.browser()
                .get(tenant.path("/tickets/" + number + "/events"))
                .body()
                .path("items");
        assertThat(events.size()).isEqualTo(3);
        assertThat(events.get(0).path("changes").get(0).path("field").asString())
                .isEqualTo("created");
        var replyChange = events.get(1);
        assertThat(replyChange.path("actor").path("name").asString()).isEqualTo("佐藤 一郎");
        assertThat(replyChange.path("changes").get(0).path("field").asString()).isEqualTo("status");
        assertThat(replyChange.path("changes").get(0).path("from").asString()).isEqualTo("open");
        assertThat(replyChange.path("changes").get(0).path("to").asString()).isEqualTo("pending");
        assertThat(replyChange.path("changes").get(1).path("field").asString()).isEqualTo("comment");
        assertThat(events.get(2)
                        .path("changes")
                        .get(0)
                        .path("to")
                        .path("public")
                        .asBoolean())
                .isFalse();

        var comments = sato.browser()
                .get(tenant.path("/tickets/" + number + "/comments"))
                .body();
        assertThat(comments.size()).isEqualTo(3);
        assertThat(comments.get(2).path("public").asBoolean()).isFalse();
    }

    @Test
    void staffApiIsClosedToRequestersAndOtherTenants() {
        var number = create(sato.browser(), Map.of()).path("number").asLong();

        assertThat(requester.browser().get(tenant.path("/tickets/" + number)).status())
                .isEqualTo(404);
        assertThat(requester.browser().get(tenant.path("/tickets")).status()).isEqualTo(404);
        assertThat(tenant.browser().get(tenant.path("/tickets/" + number)).status())
                .isEqualTo(401);

        var other = new TenantFixture(port, dispatcher, mails);
        var outsider = other.agent("他社");
        assertThat(outsider.browser().get(tenant.path("/tickets/" + number)).status())
                .as("logged in to another tenant only")
                .isEqualTo(401);
        assertThat(outsider.browser().get(other.path("/tickets/" + number)).status())
                .as("same number in another tenant")
                .isEqualTo(404);
        var foreignRequester = outsider.browser()
                .post(
                        other.path("/tickets"),
                        Map.of("requesterId", requester.id().toString(), "subject", "x", "body", "x"));
        assertThat(foreignRequester.code()).isEqualTo("ticket.requester_not_found");
    }

    @Test
    void staffCanSearchUsersAndListGroups() {
        var found =
                sato.browser().get(tenant.path("/users?role=requester&q=山田")).body();
        assertThat(found.size()).isEqualTo(1);
        assertThat(found.get(0).path("email").asString()).isEqualTo(requester.email());

        var groups = sato.browser().get(tenant.path("/groups")).body();
        assertThat(groups.get(0).path("name").asString()).isEqualTo("インフラ");
        assertThat(groups.get(0).path("members").get(0).path("name").asString()).isEqualTo("佐藤 一郎");

        assertThat(sato.browser().get(tenant.path("/users?role=boss")).status()).isEqualTo(400);
        assertThat(requester.browser().get(tenant.path("/groups")).status()).isEqualTo(404);
        assertThat(sato.browser()
                        .post(tenant.path("/admin/groups"), Map.of("name", "x"))
                        .status())
                .isEqualTo(404);
        var requesterAsMember = tenant.admin.put(
                tenant.path("/admin/groups/" + infra + "/members"),
                Map.of("userIds", List.of(requester.id().toString())));
        assertThat(requesterAsMember.code()).isEqualTo("group.member_must_be_staff");
    }
}
