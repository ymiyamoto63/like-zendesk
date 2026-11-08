package dev.likezendesk.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.likezendesk.notification.app.MailDispatcher;
import dev.likezendesk.support.ApiClient;
import dev.likezendesk.support.MailTestConfiguration;
import dev.likezendesk.support.PostgresTestConfiguration;
import dev.likezendesk.support.RecordingMailSender;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/** 招待（F-A-1）、依頼者の自己登録とメールアドレスの確認（F-A-2）、パスワードの再設定。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestConfiguration.class, MailTestConfiguration.class})
class AccountLifecycleIT {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String PASSWORD = "correct-horse";

    @LocalServerPort
    int port;

    @Autowired
    MailDispatcher dispatcher;

    @Autowired
    RecordingMailSender mails;

    private ApiClient browser() {
        return new ApiClient(port);
    }

    private static String unique(String name) {
        return name + System.nanoTime() % 1_000_000_000 + "-" + SEQ.incrementAndGet();
    }

    /** テナントを作り、その管理者としてログインしたブラウザを返す。 */
    private record AdminSession(String slug, ApiClient browser) {
        String path(String rest) {
            return "/api/t/" + slug + rest;
        }
    }

    private AdminSession newTenant() {
        var slug = unique("t");
        var browser = browser();
        var response = browser.post(
                "/api/tenants",
                Map.of(
                        "tenantName",
                        "テナント " + slug,
                        "slug",
                        slug,
                        "adminName",
                        "管理者",
                        "email",
                        unique("admin") + "@example.com",
                        "password",
                        PASSWORD));
        assertThat(response.status()).isEqualTo(201);
        return new AdminSession(slug, browser);
    }

    private ApiClient.Response login(String slug, String email, String password) {
        return browser().post("/api/t/" + slug + "/session", Map.of("email", email, "password", password));
    }

    private String tokenSentTo(String email) {
        dispatcher.dispatchDue();
        return mails.tokenSentTo(email);
    }

    @Test
    void invitedAgentSetsNameAndPasswordAndIsLoggedIn() {
        var admin = newTenant();
        var email = unique("agent") + "@example.com";

        var invite = admin.browser().post(admin.path("/admin/invitations"), Map.of("email", email, "role", "agent"));
        assertThat(invite.status()).isEqualTo(202);
        assertThat(login(admin.slug(), email, PASSWORD).status())
                .as("invited user has no password yet")
                .isEqualTo(401);

        var token = tokenSentTo(email);
        assertThat(mails.lastTo(email).getText()).contains("/t/" + admin.slug() + "/invitations/" + token);
        var invitation = browser().get(admin.path("/invitations/" + token));
        assertThat(invitation.status()).isEqualTo(200);
        assertThat(invitation.body().path("email").asString()).isEqualTo(email);

        var agent = browser();
        var accepted = agent.post(
                admin.path("/invitations/" + token + "/accept"), Map.of("name", "エージェント", "password", PASSWORD));
        assertThat(accepted.status()).isEqualTo(200);
        assertThat(accepted.body().path("role").asString()).isEqualTo("agent");
        assertThat(agent.get(admin.path("/me")).body().path("name").asString()).isEqualTo("エージェント");

        var reused = browser()
                .post(admin.path("/invitations/" + token + "/accept"), Map.of("name", "x", "password", PASSWORD));
        assertThat(reused.status()).isEqualTo(404);
        assertThat(reused.code()).isEqualTo("token.invalid");
        assertThat(login(admin.slug(), email, PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void reinvitingAPendingUserChangesTheRoleButRegisteredUsersCannotBeInvited() {
        var admin = newTenant();
        var email = unique("staff") + "@example.com";
        admin.browser().post(admin.path("/admin/invitations"), Map.of("email", email, "role", "agent"));
        admin.browser().post(admin.path("/admin/invitations"), Map.of("email", email, "role", "admin"));

        var accepted = browser()
                .post(
                        admin.path("/invitations/" + tokenSentTo(email) + "/accept"),
                        Map.of("name", "管理者2", "password", PASSWORD));
        assertThat(accepted.body().path("role").asString()).isEqualTo("admin");

        var again = admin.browser().post(admin.path("/admin/invitations"), Map.of("email", email, "role", "agent"));
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("user.email_taken");
    }

    @Test
    void onlyAdminsCanInvite() {
        var admin = newTenant();
        var email = unique("agent") + "@example.com";
        admin.browser().post(admin.path("/admin/invitations"), Map.of("email", email, "role", "agent"));
        var agent = browser();
        agent.post(
                admin.path("/invitations/" + tokenSentTo(email) + "/accept"),
                Map.of("name", "A", "password", PASSWORD));

        var byAgent = agent.post(
                admin.path("/admin/invitations"), Map.of("email", unique("x") + "@example.com", "role", "agent"));
        assertThat(byAgent.status()).isEqualTo(404);
        var anonymous = browser()
                .post(admin.path("/admin/invitations"), Map.of("email", unique("x") + "@example.com", "role", "agent"));
        assertThat(anonymous.status()).isEqualTo(401);
    }

    @Test
    void requesterRegistersVerifiesEmailAndIsLoggedIn() {
        var admin = newTenant();
        var email = unique("req") + "@customer.org";

        var registered =
                browser().post(admin.path("/requesters"), Map.of("name", "依頼者", "email", email, "password", PASSWORD));
        assertThat(registered.status()).isEqualTo(202);

        var unverified = login(admin.slug(), email, PASSWORD);
        assertThat(unverified.status()).isEqualTo(403);
        assertThat(unverified.code()).isEqualTo("login.email_unverified");
        assertThat(login(admin.slug(), email, "wrong-password").code())
                .as("wrong password does not reveal the account")
                .isEqualTo("login.invalid_credentials");

        var requester = browser();
        var verified = requester.post(admin.path("/email-verifications"), Map.of("token", tokenSentTo(email)));
        assertThat(verified.status()).isEqualTo(200);
        assertThat(verified.body().path("role").asString()).isEqualTo("requester");
        assertThat(requester.get(admin.path("/me")).status()).isEqualTo(200);
        assertThat(login(admin.slug(), email, PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void registeringAKnownAddressAnswersTheSameAndKeepsThePassword() {
        var admin = newTenant();
        var email = unique("req") + "@customer.org";
        browser().post(admin.path("/requesters"), Map.of("name", "依頼者", "email", email, "password", PASSWORD));

        var again = browser()
                .post(admin.path("/requesters"), Map.of("name", "他人", "email", email, "password", "attacker-pass"));
        assertThat(again.status()).isEqualTo(202);
        dispatcher.dispatchDue();
        assertThat(mails.countTo(email))
                .as("unverified requester gets the link again")
                .isEqualTo(2);

        browser().post(admin.path("/email-verifications"), Map.of("token", mails.tokenSentTo(email)));
        assertThat(login(admin.slug(), email, "attacker-pass").status()).isEqualTo(401);

        browser().post(admin.path("/requesters"), Map.of("name", "他人", "email", email, "password", "attacker-pass"));
        dispatcher.dispatchDue();
        assertThat(mails.countTo(email)).as("verified requester gets nothing").isEqualTo(2);
    }

    /** 受け入れ基準 12: 許可ドメイン以外のメールアドレスでは登録できない。 */
    @Test
    void domainRestrictedTenantRejectsOtherDomains() {
        var admin = newTenant();
        var updated = admin.browser()
                .patch(
                        admin.path("/admin/settings"),
                        Map.of(
                                "name",
                                "新しい名前",
                                "requesterSignup",
                                "domain_restricted",
                                "allowedDomains",
                                List.of("@Customer.org")));
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.body().path("allowedDomains").get(0).asString()).isEqualTo("customer.org");
        assertThat(browser()
                        .get("/api/t/" + admin.slug())
                        .body()
                        .path("requesterSignup")
                        .asString())
                .isEqualTo("domain_restricted");

        var rejected = browser()
                .post(
                        admin.path("/requesters"),
                        Map.of("name", "x", "email", unique("x") + "@other.org", "password", PASSWORD));
        assertThat(rejected.status()).isEqualTo(422);
        assertThat(rejected.code()).isEqualTo("requester.domain_not_allowed");
        var accepted = browser()
                .post(
                        admin.path("/requesters"),
                        Map.of("name", "x", "email", unique("x") + "@customer.org", "password", PASSWORD));
        assertThat(accepted.status()).isEqualTo(202);
    }

    @Test
    void domainRestrictionNeedsADomainAndOnlyAdminsChangeSettings() {
        var admin = newTenant();
        var empty = admin.browser()
                .patch(
                        admin.path("/admin/settings"),
                        Map.of("name", "x", "requesterSignup", "domain_restricted", "allowedDomains", List.of()));
        assertThat(empty.status()).isEqualTo(422);
        assertThat(empty.code()).isEqualTo("settings.allowed_domains_required");

        var email = unique("req") + "@customer.org";
        var requester = browser();
        requester.post(admin.path("/requesters"), Map.of("name", "依頼者", "email", email, "password", PASSWORD));
        requester.post(admin.path("/email-verifications"), Map.of("token", tokenSentTo(email)));
        assertThat(requester.get(admin.path("/admin/settings")).status()).isEqualTo(404);
    }

    @Test
    void passwordResetReplacesThePasswordOnce() {
        var admin = newTenant();
        var email = unique("req") + "@customer.org";
        browser().post(admin.path("/requesters"), Map.of("name", "依頼者", "email", email, "password", PASSWORD));
        dispatcher.dispatchDue();

        var unknown =
                browser().post(admin.path("/password-resets"), Map.of("email", unique("nobody") + "@customer.org"));
        assertThat(unknown.status()).isEqualTo(202);

        assertThat(browser()
                        .post(admin.path("/password-resets"), Map.of("email", email))
                        .status())
                .isEqualTo(202);
        var token = tokenSentTo(email);
        assertThat(mails.lastTo(email).getSubject()).contains("パスワードの再設定");

        assertThat(browser()
                        .put(admin.path("/password-resets/" + token), Map.of("password", "new-password"))
                        .status())
                .isEqualTo(204);
        assertThat(browser()
                        .put(admin.path("/password-resets/" + token), Map.of("password", "other-password"))
                        .code())
                .isEqualTo("token.invalid");
        assertThat(login(admin.slug(), email, PASSWORD).status()).isEqualTo(401);
        assertThat(login(admin.slug(), email, "new-password").status())
                .as("reset link proves the address, so the requester is verified too")
                .isEqualTo(200);
    }

    @Test
    void tokensDoNotWorkInAnotherTenant() {
        var admin = newTenant();
        var other = newTenant();
        var email = unique("agent") + "@example.com";
        admin.browser().post(admin.path("/admin/invitations"), Map.of("email", email, "role", "agent"));
        var token = tokenSentTo(email);

        assertThat(browser().get(other.path("/invitations/" + token)).status()).isEqualTo(404);
        assertThat(browser().get(admin.path("/invitations/" + token)).status()).isEqualTo(200);
    }
}
