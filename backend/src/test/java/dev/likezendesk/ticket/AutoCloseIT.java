package dev.likezendesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.likezendesk.notification.app.MailDispatcher;
import dev.likezendesk.support.MailTestConfiguration;
import dev.likezendesk.support.PostgresTestConfiguration;
import dev.likezendesk.support.RecordingMailSender;
import dev.likezendesk.support.TenantFixture;
import dev.likezendesk.ticket.app.AutoClose;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 解決から 4 日たったチケットの自動クローズ（要件 7、受け入れ基準 8）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestConfiguration.class, MailTestConfiguration.class})
class AutoCloseIT {

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

    @Test
    void closesTicketsSolvedFourDaysAgo() {
        var tenant = new TenantFixture(port, dispatcher, mails);
        var sato = tenant.agent("佐藤 一郎");
        var requester = tenant.requester("山田 花子");
        UUID infra = tenant.group("インフラ", sato);
        for (int i = 0; i < 3; i++) {
            var created = sato.browser()
                    .post(
                            tenant.path("/tickets"),
                            Map.of(
                                    "requesterId", requester.id().toString(),
                                    "subject", "問い合わせ " + i,
                                    "body", "本文",
                                    "groupId", infra.toString(),
                                    "assigneeId", sato.id().toString()));
            assertThat(created.status()).isEqualTo(201);
        }
        for (long number : new long[] {1, 2}) {
            assertThat(sato.browser()
                            .patch(tenant.path("/tickets/" + number), Map.of("version", 0, "status", "solved"))
                            .status())
                    .isEqualTo(200);
        }
        // 1 は 4 日と 1 分前、2 は 3 日前に解決したことにする。3 はオープンのまま
        solvedAgo(tenant, 1, ChronoUnit.MINUTES, 4 * 24 * 60 + 1);
        solvedAgo(tenant, 2, ChronoUnit.DAYS, 3);

        assertThat(autoClose.closeDue()).isGreaterThanOrEqualTo(1);

        assertThat(status(tenant, sato, 1)).isEqualTo("closed");
        assertThat(status(tenant, sato, 2)).isEqualTo("solved");
        assertThat(status(tenant, sato, 3)).isEqualTo("open");

        // 監査ログの実行者はシステム（null）
        var events = sato.browser().get(tenant.path("/tickets/1/events")).body().path("items");
        var last = StreamSupport.stream(events.spliterator(), false)
                .reduce((a, b) -> b)
                .orElseThrow();
        assertThat(last.path("actor").isNull()).isTrue();
        assertThat(last.path("changes").toString()).contains("closed");

        // クローズ後は変更できない
        var change = sato.browser().patch(tenant.path("/tickets/1"), Map.of("version", 2, "priority", "high"));
        assertThat(change.status()).isEqualTo(422);
        assertThat(change.code()).isEqualTo("status.closed_is_final");

        // もう一度動かしても何も起きない
        assertThat(status(tenant, sato, 1)).isEqualTo("closed");
        autoClose.closeDue();
        assertThat(sato.browser()
                        .get(tenant.path("/tickets/1"))
                        .body()
                        .path("version")
                        .asInt())
                .isEqualTo(2);
    }

    private void solvedAgo(TenantFixture tenant, long number, ChronoUnit unit, long amount) {
        jdbc.sql("""
                        UPDATE tickets SET solved_at = :at
                        WHERE number = :number AND tenant_id = (SELECT id FROM tenants WHERE slug = :slug)
                        """)
                .param("at", Timestamp.from(Instant.now().minus(amount, unit)))
                .param("number", number)
                .param("slug", tenant.slug)
                .update();
    }

    private static String status(TenantFixture tenant, TenantFixture.User user, long number) {
        return user.browser()
                .get(tenant.path("/tickets/" + number))
                .body()
                .path("status")
                .asString();
    }
}
