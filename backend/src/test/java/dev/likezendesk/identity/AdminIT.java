package dev.likezendesk.identity;

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
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/** 管理画面: ユーザー、グループ、所属組織（F-A-1、F-A-3、F-A-4）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestConfiguration.class, MailTestConfiguration.class})
class AdminIT {

    @LocalServerPort
    int port;

    @Autowired
    MailDispatcher dispatcher;

    @Autowired
    RecordingMailSender mails;

    TenantFixture tenant;
    TenantFixture.User sato;
    TenantFixture.User requester;

    @BeforeEach
    void setUp() {
        tenant = new TenantFixture(port, dispatcher, mails);
        sato = tenant.agent("佐藤 一郎");
        requester = tenant.requester("山田 花子");
    }

    private List<String> names(ApiClient.Response response) {
        assertThat(response.status()).isEqualTo(200);
        return StreamSupport.stream(response.body().path("items").spliterator(), false)
                .map(u -> u.path("name").asString())
                .toList();
    }

    private UUID adminId() {
        return UUID.fromString(
                tenant.admin.get(tenant.path("/me")).body().path("id").asString());
    }

    @Test
    void listsUsersWithFilters() {
        assertThat(tenant.admin
                        .post(
                                tenant.path("/admin/invitations"),
                                Map.of("email", "new@" + tenant.slug + ".test", "role", "agent"))
                        .status())
                .isEqualTo(202);

        var all = tenant.admin.get(tenant.path("/admin/users"));
        assertThat(all.body().path("total").asLong()).isEqualTo(4);
        assertThat(names(tenant.admin.get(tenant.path("/admin/users?role=requester"))))
                .containsExactly("山田 花子");
        var invited = tenant.admin.get(tenant.path("/admin/users?status=invited"));
        assertThat(invited.body().path("total").asLong()).isEqualTo(1);
        assertThat(invited.body().path("items").get(0).path("email").asString()).startsWith("new@");
        assertThat(names(tenant.admin.get(tenant.path("/admin/users?q=" + "%E4%BD%90%E8%97%A4"))))
                .containsExactly("佐藤 一郎");
        assertThat(tenant.admin
                        .get(tenant.path("/admin/users?size=1&page=1"))
                        .body()
                        .path("items")
                        .size())
                .isEqualTo(1);

        assertThat(sato.browser().get(tenant.path("/admin/users")).status()).isEqualTo(404);
        assertThat(tenant.admin.get(tenant.path("/admin/users?status=gone")).status())
                .isEqualTo(400);
    }

    @Test
    void changesRolesAndDeactivatesUsers() {
        var promoted = tenant.admin.patch(tenant.path("/admin/users/" + sato.id()), Map.of("role", "admin"));
        assertThat(promoted.status()).isEqualTo(200);
        assertThat(promoted.body().path("role").asString()).isEqualTo("admin");
        // ロールは毎回 DB から読むので、すぐに管理画面に入れる
        assertThat(sato.browser().get(tenant.path("/admin/users")).status()).isEqualTo(200);

        var deactivated = tenant.admin.patch(tenant.path("/admin/users/" + sato.id()), Map.of("active", false));
        assertThat(deactivated.body().path("status").asString()).isEqualTo("inactive");
        assertThat(sato.browser().get(tenant.path("/tickets")).status()).isIn(401, 404);

        var toRequester = tenant.admin.patch(tenant.path("/admin/users/" + requester.id()), Map.of("role", "agent"));
        assertThat(toRequester.status()).isEqualTo(422);
        assertThat(toRequester.code()).isEqualTo("user.role_change_not_allowed");
    }

    @Test
    void adminsCannotLockThemselvesOut() {
        var me = adminId();
        var demote = tenant.admin.patch(tenant.path("/admin/users/" + me), Map.of("role", "agent"));
        assertThat(demote.status()).isEqualTo(422);
        assertThat(demote.code()).isEqualTo("user.cannot_change_self");
        assertThat(tenant.admin
                        .patch(tenant.path("/admin/users/" + me), Map.of("active", false))
                        .code())
                .isEqualTo("user.cannot_change_self");
        // 変えない値を送るのは構わない
        assertThat(tenant.admin
                        .patch(tenant.path("/admin/users/" + me), Map.of("role", "admin", "active", true))
                        .status())
                .isEqualTo(200);
    }

    @Test
    void organizationsGroupRequestersForViews() {
        var created = tenant.admin.post(tenant.path("/admin/organizations"), Map.of("name", "営業部"));
        assertThat(created.status()).isEqualTo(201);
        var org = created.body().path("id").asString();
        assertThat(tenant.admin
                        .post(tenant.path("/admin/organizations"), Map.of("name", "営業部"))
                        .code())
                .isEqualTo("organization.name_taken");

        var assigned = tenant.admin.patch(tenant.path("/admin/users/" + requester.id()), Map.of("organizationId", org));
        assertThat(assigned.status()).isEqualTo(200);
        assertThat(assigned.body().path("organization").path("name").asString()).isEqualTo("営業部");
        assertThat(tenant.admin
                        .patch(tenant.path("/admin/users/" + sato.id()), Map.of("organizationId", org))
                        .code())
                .isEqualTo("user.organization_for_requesters");

        var list = sato.browser().get(tenant.path("/organizations"));
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body().get(0).path("members").asLong()).isEqualTo(1);
        assertThat(requester.browser().get(tenant.path("/organizations")).status())
                .isEqualTo(404);
        assertThat(sato.browser()
                        .post(tenant.path("/admin/organizations"), Map.of("name", "x"))
                        .status())
                .isEqualTo(404);

        // 依頼者の所属組織でチケットを絞り込む
        var other = tenant.requester("田中 三郎");
        for (var r : List.of(requester, other)) {
            assertThat(sato.browser()
                            .post(
                                    tenant.path("/tickets"),
                                    Map.of("requesterId", r.id().toString(), "subject", "件名", "body", "本文"))
                            .status())
                    .isEqualTo(201);
        }
        var view = sato.browser()
                .post(
                        tenant.path("/views"),
                        Map.of(
                                "name",
                                "営業部",
                                "conditions",
                                Map.of(
                                        "all",
                                        List.of(Map.of("field", "organization", "op", "in", "value", List.of(org))))));
        assertThat(view.status()).isEqualTo(201);
        var tickets = sato.browser()
                .get(tenant.path("/tickets?view=" + view.body().path("id").asString()));
        assertThat(tickets.body().path("total").asLong()).isEqualTo(1);
        assertThat(tickets.body().path("items").get(0).path("number").asLong()).isEqualTo(1);

        var renamed = tenant.admin.patch(tenant.path("/admin/organizations/" + org), Map.of("name", "営業本部"));
        assertThat(renamed.body().path("name").asString()).isEqualTo("営業本部");

        // 削除すると所属なしになる
        assertThat(tenant.admin
                        .delete(tenant.path("/admin/organizations/" + org))
                        .status())
                .isEqualTo(204);
        var after = tenant.admin.get(tenant.path("/admin/users?role=requester&q=" + "%E5%B1%B1%E7%94%B0"));
        assertThat(after.body().path("items").get(0).path("organization").isNull())
                .isTrue();

        // null を送ると所属なし、省略すると変えない
        var org2 = tenant.admin
                .post(tenant.path("/admin/organizations"), Map.of("name", "経理部"))
                .body()
                .path("id")
                .asString();
        tenant.admin.patch(tenant.path("/admin/users/" + requester.id()), Map.of("organizationId", org2));
        assertThat(tenant.admin
                        .patch(tenant.path("/admin/users/" + requester.id()), Map.of("active", true))
                        .body()
                        .path("organization")
                        .path("id")
                        .asString())
                .isEqualTo(org2);
        var cleared = new HashMap<String, Object>();
        cleared.put("organizationId", null);
        assertThat(tenant.admin
                        .patch(tenant.path("/admin/users/" + requester.id()), cleared)
                        .body()
                        .path("organization")
                        .isNull())
                .isTrue();
    }

    @Test
    void groupsInUseCannotBeDeleted() {
        var infra = tenant.group("インフラ", sato);
        var empty = tenant.group("空き", sato);
        assertThat(tenant.admin
                        .patch(tenant.path("/admin/groups/" + empty), Map.of("name", "インフラ"))
                        .code())
                .isEqualTo("group.name_taken");
        assertThat(tenant.admin
                        .patch(tenant.path("/admin/groups/" + empty), Map.of("name", "予備"))
                        .body()
                        .path("name")
                        .asString())
                .isEqualTo("予備");

        assertThat(sato.browser()
                        .post(
                                tenant.path("/tickets"),
                                Map.of(
                                        "requesterId",
                                        requester.id().toString(),
                                        "subject",
                                        "件名",
                                        "body",
                                        "本文",
                                        "groupId",
                                        infra.toString()))
                        .status())
                .isEqualTo(201);
        var inUse = tenant.admin.delete(tenant.path("/admin/groups/" + infra));
        assertThat(inUse.status()).isEqualTo(409);
        assertThat(inUse.code()).isEqualTo("group.in_use");

        assertThat(tenant.admin.delete(tenant.path("/admin/groups/" + empty)).status())
                .isEqualTo(204);
        assertThat(tenant.admin.delete(tenant.path("/admin/groups/" + empty)).status())
                .isEqualTo(404);
        assertThat(sato.browser().delete(tenant.path("/admin/groups/" + infra)).status())
                .isEqualTo(404);
    }

    @Test
    void otherTenantsCannotTouchUsers() {
        var other = new TenantFixture(port, dispatcher, mails);
        assertThat(other.admin
                        .patch(other.path("/admin/users/" + sato.id()), Map.of("active", false))
                        .status())
                .isEqualTo(404);
        var org = tenant.admin
                .post(tenant.path("/admin/organizations"), Map.of("name", "営業部"))
                .body()
                .path("id")
                .asString();
        assertThat(other.admin.delete(other.path("/admin/organizations/" + org)).status())
                .isEqualTo(404);
        var otherRequester = other.requester("他社");
        assertThat(tenant.admin
                        .patch(
                                tenant.path("/admin/users/" + requester.id()),
                                Map.of("organizationId", UUID.randomUUID().toString()))
                        .code())
                .isEqualTo("organization.not_found");
        assertThat(other.admin
                        .patch(other.path("/admin/users/" + otherRequester.id()), Map.of("organizationId", org))
                        .code())
                .isEqualTo("organization.not_found");
    }
}
