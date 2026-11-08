package dev.likezendesk.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MailTemplatesTest {

    private static final Map<String, String> TICKET = Map.of(
            "to", "hanako@example.com",
            "tenantName", "デモ社",
            "url", "https://example.com/t/demo/requests/12",
            "number", "12",
            "subject", "VPN に接続できない",
            "body", "ログを添付してください。",
            "author", "佐藤 一郎");

    @Test
    void ticketMailsNameTheTenantAndTicketInTheSubject() {
        var mail = MailTemplates.render(NotificationKind.PUBLIC_COMMENT, TICKET);
        assertThat(mail.to()).isEqualTo("hanako@example.com");
        assertThat(mail.subject()).isEqualTo("[デモ社] #12 VPN に接続できない - 返信がありました");
        assertThat(mail.body()).contains("佐藤 一郎 さんが返信しました。", "ログを添付してください。", "https://example.com/t/demo/requests/12");
    }

    @Test
    void everyTicketKindRenders() {
        for (var kind : new NotificationKind[] {
            NotificationKind.TICKET_CREATED,
            NotificationKind.PUBLIC_COMMENT,
            NotificationKind.ASSIGNED,
            NotificationKind.SOLVED
        }) {
            var mail = MailTemplates.render(kind, TICKET);
            assertThat(mail.subject()).startsWith("[デモ社] #12 ");
            assertThat(mail.body()).contains(TICKET.get("url")).doesNotContain("null");
        }
    }
}
