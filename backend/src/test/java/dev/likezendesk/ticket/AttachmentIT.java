package dev.likezendesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.likezendesk.notification.app.MailDispatcher;
import dev.likezendesk.support.MailTestConfiguration;
import dev.likezendesk.support.PostgresTestConfiguration;
import dev.likezendesk.support.RecordingMailSender;
import dev.likezendesk.support.TenantFixture;
import dev.likezendesk.ticket.app.Attachments;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 添付ファイル（F-T-7、architecture.md 2.9）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestConfiguration.class, MailTestConfiguration.class})
class AttachmentIT {

    private static final byte[] LOG = "2026-11-08 ERROR VPN timeout\n".getBytes(StandardCharsets.UTF_8);

    @LocalServerPort
    int port;

    @Autowired
    MailDispatcher dispatcher;

    @Autowired
    RecordingMailSender mails;

    @Autowired
    Attachments attachments;

    @Autowired
    JdbcClient jdbc;

    TenantFixture tenant;
    TenantFixture.User sato;
    TenantFixture.User requester;

    @BeforeEach
    void setUp() {
        tenant = new TenantFixture(port, dispatcher, mails);
        sato = tenant.agent("佐藤 一郎");
        requester = tenant.requester("山田 花子");
    }

    private String upload(TenantFixture.User user, String filename, String type, byte[] content) {
        var uploaded = user.browser().upload(tenant.path("/attachments"), filename, type, content);
        assertThat(uploaded.status()).isEqualTo(201);
        return uploaded.body().path("id").asString();
    }

    @Test
    void requestersAttachFilesAndAgentsDownloadThem() {
        var id = upload(requester, "vpn ログ.txt", "text/plain; charset=utf-8", LOG);
        var created = requester
                .browser()
                .post(
                        tenant.path("/portal/tickets"),
                        Map.of("subject", "VPN", "body", "ログを付けます。", "attachmentIds", List.of(id)));
        assertThat(created.status()).isEqualTo(201);
        var number = created.body().path("number").asLong();

        var comments = sato.browser().get(tenant.path("/tickets/" + number + "/comments"));
        var file = comments.body().get(0).path("attachments").get(0);
        assertThat(file.path("filename").asString()).isEqualTo("vpn ログ.txt");
        assertThat(file.path("contentType").asString()).isEqualTo("text/plain");
        assertThat(file.path("size").asLong()).isEqualTo(LOG.length);
        assertThat(file.has("storageKey")).isFalse();

        var download = sato.browser().raw(tenant.path("/attachments/" + id));
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(LOG);
        assertThat(download.headers().firstValue("Content-Disposition").orElseThrow())
                .startsWith("attachment;")
                .contains("filename*=UTF-8''vpn%20%E3%83%AD%E3%82%B0.txt");
        assertThat(download.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");

        // 1 度紐付けた添付は、別のコメントに使えない
        var reuse = requester
                .browser()
                .post(
                        tenant.path("/portal/tickets/" + number + "/comments"),
                        Map.of("body", "もう一度", "attachmentIds", List.of(id)));
        assertThat(reuse.status()).isEqualTo(422);
        assertThat(reuse.code()).isEqualTo("attachment.not_found");
    }

    @Test
    void internalNoteAttachmentsAreHiddenFromRequesters() {
        var number = requester
                .browser()
                .post(tenant.path("/portal/tickets"), Map.of("subject", "VPN", "body", "接続できません。"))
                .body()
                .path("number")
                .asLong();
        var note = upload(sato, "調査メモ.html", "text/html", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        var reply = upload(sato, "手順.pdf", "application/pdf", new byte[] {'%', 'P', 'D', 'F'});
        assertThat(sato.browser()
                        .post(
                                tenant.path("/tickets/" + number + "/comments"),
                                Map.of("body", "社内メモ", "public", false, "attachmentIds", List.of(note)))
                        .status())
                .isEqualTo(201);
        assertThat(sato.browser()
                        .post(
                                tenant.path("/tickets/" + number + "/comments"),
                                Map.of("body", "手順です", "public", true, "attachmentIds", List.of(reply)))
                        .status())
                .isEqualTo(201);

        assertThat(requester.browser().raw(tenant.path("/attachments/" + note)).statusCode())
                .isEqualTo(404);
        assertThat(requester.browser().raw(tenant.path("/attachments/" + reply)).statusCode())
                .isEqualTo(200);
        var portal = requester.browser().get(tenant.path("/portal/tickets/" + number + "/comments"));
        assertThat(portal.body().toString()).contains("手順.pdf").doesNotContain("調査メモ");

        // HTML もダウンロードさせる（ブラウザで実行させない）
        var html = sato.browser().raw(tenant.path("/attachments/" + note));
        assertThat(html.headers().firstValue("Content-Disposition").orElseThrow())
                .startsWith("attachment;");
    }

    @Test
    void unattachedFilesBelongToTheUploaderOnly() {
        var id = upload(requester, "../../etc/passwd", "evil type", LOG);
        var mine = requester.browser().raw(tenant.path("/attachments/" + id));
        assertThat(mine.statusCode()).isEqualTo(200);
        assertThat(mine.headers().firstValue("Content-Type")).hasValue("application/octet-stream");
        assertThat(mine.headers().firstValue("Content-Disposition").orElseThrow())
                .contains("passwd")
                .doesNotContain("..");

        assertThat(sato.browser().raw(tenant.path("/attachments/" + id)).statusCode())
                .isEqualTo(404);
        var other = new TenantFixture(port, dispatcher, mails);
        assertThat(other.admin.raw(other.path("/attachments/" + id)).statusCode())
                .isEqualTo(404);
        assertThat(tenant.browser().raw(tenant.path("/attachments/" + id)).statusCode())
                .isIn(401, 404);

        // 他人の添付はコメントに付けられない
        var number = requester
                .browser()
                .post(tenant.path("/portal/tickets"), Map.of("subject", "VPN", "body", "本文"))
                .body()
                .path("number")
                .asLong();
        var stolen = sato.browser()
                .post(
                        tenant.path("/tickets/" + number + "/comments"),
                        Map.of("body", "x", "public", true, "attachmentIds", List.of(id)));
        assertThat(stolen.code()).isEqualTo("attachment.not_found");
        // 失敗したコメントは残らない
        assertThat(sato.browser()
                        .get(tenant.path("/tickets/" + number + "/comments"))
                        .body()
                        .size())
                .isEqualTo(1);
    }

    @Test
    void rejectsEmptyAndOversizedFiles() {
        var empty = sato.browser().upload(tenant.path("/attachments"), "empty.txt", "text/plain", new byte[0]);
        assertThat(empty.status()).isEqualTo(422);
        assertThat(empty.code()).isEqualTo("attachment.empty");

        var big = sato.browser()
                .upload(
                        tenant.path("/attachments"),
                        "big.bin",
                        "application/octet-stream",
                        new byte[20 * 1024 * 1024 + 1]);
        assertThat(big.status()).isEqualTo(413);
        assertThat(big.code()).isEqualTo("attachment.too_large");

        var ok = sato.browser()
                .upload(tenant.path("/attachments"), "max.bin", "application/octet-stream", new byte[20 * 1024 * 1024]);
        assertThat(ok.status()).isEqualTo(201);
    }

    @Test
    void deletesFilesLeftUnattachedForADay() {
        var old = UUID.fromString(upload(sato, "old.txt", "text/plain", LOG));
        var fresh = UUID.fromString(upload(sato, "fresh.txt", "text/plain", LOG));
        jdbc.sql("UPDATE attachments SET created_at = created_at - interval '25 hours' WHERE id = :id")
                .param("id", old)
                .update();

        assertThat(attachments.deleteUnattached()).isGreaterThanOrEqualTo(1);
        assertThat(sato.browser().raw(tenant.path("/attachments/" + old)).statusCode())
                .isEqualTo(404);
        assertThat(sato.browser().raw(tenant.path("/attachments/" + fresh)).statusCode())
                .isEqualTo(200);
    }
}
