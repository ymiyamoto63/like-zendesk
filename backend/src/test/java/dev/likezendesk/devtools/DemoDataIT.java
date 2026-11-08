package dev.likezendesk.devtools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.likezendesk.devtools.app.DemoData;
import dev.likezendesk.support.ApiClient;
import dev.likezendesk.support.MailTestConfiguration;
import dev.likezendesk.support.PostgresTestConfiguration;
import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** デモデータ（F-D-1）と E2E 用の時計操作（api.md 2.6）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestConfiguration.class, MailTestConfiguration.class})
@ActiveProfiles("e2e")
class DemoDataIT {

    @LocalServerPort
    int port;

    @Autowired
    DemoData demo;

    @Test
    void seedsDemoTenantsOnceAndAgentsCanSignIn() {
        demo.seed();
        assertThat(demo.seed()).isZero();

        for (var account : Map.of(
                        "demo-support", "admin@support.example.com",
                        "demo-helpdesk", "ito@corp.example.co.jp")
                .entrySet()) {
            var browser = new ApiClient(port);
            var login = browser.post(
                    "/api/t/" + account.getKey() + "/session",
                    Map.of("email", account.getValue(), "password", DemoData.PASSWORD));
            assertThat(login.status()).isEqualTo(200);

            var list = browser.get("/api/t/" + account.getKey() + "/tickets?size=100");
            assertThat(list.status()).isEqualTo(200);
            assertThat(list.body().path("total").asLong()).isGreaterThanOrEqualTo(9);
            var statuses = new HashSet<String>();
            list.body().path("items").forEach(t -> statuses.add(t.path("status").asString()));
            // 一覧やビューを試せるよう、状態が散らばっている
            assertThat(statuses).contains("new", "open", "pending", "solved", "closed");
        }
    }

    @Test
    void advancesTheClock() {
        var browser = new ApiClient(port);
        var before = browser.post("/api/_e2e/clock", Map.of("advance", "PT0S"));
        assertThat(before.status()).isEqualTo(200);

        var after = browser.post("/api/_e2e/clock", Map.of("advance", "P4DT1H"));
        assertThat(after.status()).isEqualTo(200);
        assertThat(java.time.Instant.parse(after.body().path("now").asString()))
                .isAfterOrEqualTo(
                        java.time.Instant.parse(before.body().path("now").asString())
                                .plus(java.time.Duration.ofHours(97)));

        assertThat(browser.post("/api/_e2e/clock", Map.of("advance", "-PT1H")).code())
                .isEqualTo("clock.invalid_duration");
        assertThat(browser.post("/api/_e2e/clock", Map.of("advance", "soon")).code())
                .isEqualTo("clock.invalid_duration");
    }
}
