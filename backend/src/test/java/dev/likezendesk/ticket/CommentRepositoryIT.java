package dev.likezendesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.likezendesk.notification.app.MailDispatcher;
import dev.likezendesk.shared.TenantId;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.support.MailTestConfiguration;
import dev.likezendesk.support.PostgresTestConfiguration;
import dev.likezendesk.support.RecordingMailSender;
import dev.likezendesk.support.TenantFixture;
import dev.likezendesk.ticket.infra.CommentRepository;
import dev.likezendesk.ticket.infra.TicketRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** コメントの書き込みは、呼び出し側の確認を当てにせず、チケットが閲覧者に見えるときだけ成功する。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestConfiguration.class, MailTestConfiguration.class})
class CommentRepositoryIT {

    @LocalServerPort
    int port;

    @Autowired
    MailDispatcher dispatcher;

    @Autowired
    RecordingMailSender mails;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    CommentRepository comments;

    @Autowired
    TicketRepository tickets;

    TenantFixture tenant;
    TenantFixture.User sato;
    TenantFixture.User hanako;
    TenantFixture.User taro;
    UUID ticketId;

    @BeforeEach
    void setUp() {
        tenant = new TenantFixture(port, dispatcher, mails);
        sato = tenant.agent("佐藤 一郎");
        hanako = tenant.requester("山田 花子");
        taro = tenant.requester("鈴木 太郎");
        var created = sato.browser()
                .post(
                        tenant.path("/tickets"),
                        Map.of("requesterId", hanako.id().toString(), "subject", "VPN", "body", "接続できません"));
        assertThat(created.status()).isEqualTo(201);
        ticketId = tickets.find(staff(sato), created.body().path("number").asLong())
                .orElseThrow()
                .id();
    }

    private TenantId tenantId(String slug) {
        return new TenantId(jdbc.sql("SELECT id FROM tenants WHERE slug = :slug")
                .param("slug", slug)
                .query(UUID.class)
                .single());
    }

    private Viewer.Agent staff(TenantFixture.User user) {
        return new Viewer.Agent(tenantId(tenant.slug), new UserId(user.id()));
    }

    private Viewer.Requester requester(TenantFixture.User user) {
        return new Viewer.Requester(tenantId(tenant.slug), new UserId(user.id()));
    }

    @Test
    void writesWhenTheTicketIsVisible() {
        assertThat(comments.insert(staff(sato), ticketId, new UserId(sato.id()), "確認します", false, Instant.now()))
                .isPresent();
        assertThat(comments.insert(requester(hanako), ticketId, new UserId(hanako.id()), "追記", true, Instant.now()))
                .isPresent();
    }

    @Test
    void refusesToWriteOnATicketTheViewerCannotSee() {
        assertThat(comments.insert(requester(taro), ticketId, new UserId(taro.id()), "のぞき見", true, Instant.now()))
                .isEmpty();

        var otherTenant = new TenantFixture(port, dispatcher, mails);
        var outsider = otherTenant.agent("他社の人");
        var foreign = new Viewer.Agent(tenantId(otherTenant.slug), new UserId(outsider.id()));
        assertThat(comments.insert(foreign, ticketId, new UserId(outsider.id()), "乗っ取り", true, Instant.now()))
                .isEmpty();

        assertThat(comments.list(staff(sato), ticketId)).hasSize(1);
    }

    @Test
    void requestersCannotWriteInternalNotes() {
        assertThatThrownBy(() -> comments.insert(
                        requester(hanako), ticketId, new UserId(hanako.id()), "社内メモ", false, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
