package dev.likezendesk.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.likezendesk.support.ApiClient;
import dev.likezendesk.support.PostgresTestConfiguration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/** サインアップ、ログイン、ログアウト、テナント単位のログイン状態（F-X-1、F-X-2、F-X-4、N-3）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class AuthenticationIT {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @LocalServerPort
    int port;

    private ApiClient browser() {
        return new ApiClient(port);
    }

    /** テストごとに別のテナントを使い、DB の後片付けを要らなくする。 */
    private static String newSlug() {
        return "t" + System.nanoTime() % 1_000_000_000 + "-" + SEQ.incrementAndGet();
    }

    private static Map<String, String> signupBody(String slug, String email) {
        return Map.of(
                "tenantName",
                "テナント " + slug,
                "slug",
                slug,
                "adminName",
                "管理者",
                "email",
                email,
                "password",
                "correct-horse");
    }

    private String signup(ApiClient browser, String email) {
        var slug = newSlug();
        var response = browser.post("/api/tenants", signupBody(slug, email));
        assertThat(response.status()).isEqualTo(201);
        return slug;
    }

    @Test
    void signupCreatesTenantAndLogsInAsAdmin() {
        var browser = browser();
        var slug = signup(browser, "Admin@Example.com");

        var me = browser.get("/api/t/" + slug + "/me");
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.body().path("role").asString()).isEqualTo("admin");
        assertThat(me.body().path("email").asString()).isEqualTo("admin@example.com");

        var tenant = browser().get("/api/t/" + slug);
        assertThat(tenant.status()).isEqualTo(200);
        assertThat(tenant.body().path("name").asString()).isEqualTo("テナント " + slug);
        assertThat(tenant.body().path("requesterSignup").asString()).isEqualTo("open");
    }

    @Test
    void signupRejectsTakenReservedAndMalformedSlugs() {
        var slug = signup(browser(), "a@example.com");

        var taken = browser().post("/api/tenants", signupBody(slug, "b@example.com"));
        assertThat(taken.status()).isEqualTo(409);
        assertThat(taken.code()).isEqualTo("slug.taken");

        var reserved = browser().post("/api/tenants", signupBody("admin", "b@example.com"));
        assertThat(reserved.status()).isEqualTo(422);
        assertThat(reserved.code()).isEqualTo("slug.reserved");

        var malformed = browser().post("/api/tenants", signupBody("Bad_Slug", "b@example.com"));
        assertThat(malformed.status()).isEqualTo(400);
        assertThat(malformed.code()).isEqualTo("validation_failed");
        assertThat(malformed.body().path("errors").get(0).path("field").asString())
                .isEqualTo("slug");
    }

    @Test
    void loginAndLogout() {
        var slug = signup(browser(), "admin@example.com");
        var browser = browser();

        assertThat(browser.get("/api/t/" + slug + "/me").status()).isEqualTo(401);

        var login = browser.post(
                "/api/t/" + slug + "/session", Map.of("email", "ADMIN@example.com", "password", "correct-horse"));
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.body().path("role").asString()).isEqualTo("admin");
        assertThat(browser.get("/api/t/" + slug + "/me").status()).isEqualTo(200);

        assertThat(browser.delete("/api/t/" + slug + "/session").status()).isEqualTo(204);
        assertThat(browser.get("/api/t/" + slug + "/me").status()).isEqualTo(401);
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() {
        var slug = signup(browser(), "admin@example.com");

        var wrongPassword = browser()
                .post("/api/t/" + slug + "/session", Map.of("email", "admin@example.com", "password", "wrong-pass"));
        var unknownEmail = browser()
                .post("/api/t/" + slug + "/session", Map.of("email", "nobody@example.com", "password", "wrong-pass"));

        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(wrongPassword.body()).isEqualTo(unknownEmail.body());
        assertThat(wrongPassword.code()).isEqualTo("login.invalid_credentials");
    }

    @Test
    void loginIsLockedAfterTenFailuresEvenWithTheRightPassword() {
        var slug = signup(browser(), "admin@example.com");
        var browser = browser();
        for (int i = 0; i < 10; i++) {
            browser.post("/api/t/" + slug + "/session", Map.of("email", "admin@example.com", "password", "wrong-pass"));
        }

        var locked = browser.post(
                "/api/t/" + slug + "/session", Map.of("email", "admin@example.com", "password", "correct-horse"));

        assertThat(locked.status()).isEqualTo(429);
        assertThat(locked.code()).isEqualTo("login.too_many_attempts");
    }

    @Test
    void loginStateIsPerTenant() {
        var browser = browser();
        // F-X-4: 同じメールアドレスで、別のテナントに別のユーザーとして登録できる
        var tenantA = signup(browser, "same@example.com");
        var tenantB = signup(browser, "same@example.com");

        assertThat(browser.get("/api/t/" + tenantA + "/me").status()).isEqualTo(200);
        assertThat(browser.get("/api/t/" + tenantB + "/me").status()).isEqualTo(200);
        assertThat(browser.get("/api/t/" + tenantA + "/me").body().path("id"))
                .isNotEqualTo(browser.get("/api/t/" + tenantB + "/me").body().path("id"));

        assertThat(browser.delete("/api/t/" + tenantA + "/session").status()).isEqualTo(204);
        assertThat(browser.get("/api/t/" + tenantA + "/me").status()).isEqualTo(401);
        assertThat(browser.get("/api/t/" + tenantB + "/me").status()).isEqualTo(200);
    }

    @Test
    void loginToOneTenantDoesNotGrantAnother() {
        var other = signup(browser(), "admin@example.com");
        var browser = browser();
        signup(browser, "admin@example.com");

        assertThat(browser.get("/api/t/" + other + "/me").status()).isEqualTo(401);
    }

    @Test
    void unknownTenantIsNotFound() {
        var response = browser().get("/api/t/no-such-tenant/me");

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.code()).isEqualTo("not_found");
    }

    @Test
    void stateChangingRequestsRequireTheCsrfToken() {
        var response = browser().withoutCsrfToken().post("/api/tenants", signupBody(newSlug(), "a@example.com"));

        assertThat(response.status()).isEqualTo(403);
    }
}
