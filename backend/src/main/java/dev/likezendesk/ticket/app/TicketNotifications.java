package dev.likezendesk.ticket.app;

import dev.likezendesk.identity.app.Directory;
import dev.likezendesk.identity.domain.Role;
import dev.likezendesk.identity.domain.UserAccount;
import dev.likezendesk.notification.app.Notifications;
import dev.likezendesk.notification.domain.NotificationKind;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.tenant.app.Tenants;
import dev.likezendesk.ticket.domain.Ref;
import dev.likezendesk.ticket.domain.Ticket;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * チケットのメール通知（F-N-1〜3）。チケットの更新と同じトランザクションで Outbox に書く。
 *
 * <p>受付メールを除き、自分の操作を自分に知らせることはしない。無効にした利用者には送らない。社内メモは誰にも送らない。
 */
@Component
class TicketNotifications {

    private final Notifications notifications;
    private final Directory directory;
    private final Tenants tenants;
    private final String baseUrl;

    TicketNotifications(
            Notifications notifications,
            Directory directory,
            Tenants tenants,
            @Value("${app.base-url}") String baseUrl) {
        this.notifications = notifications;
        this.directory = directory;
        this.tenants = tenants;
        this.baseUrl = baseUrl;
    }

    /** 受付メールを依頼者に（F-N-1）。依頼者が自分で起票しても送る。作成時に担当者がいれば、その人にも知らせる（F-N-3）。 */
    void created(Viewer viewer, Ticket ticket, String body, UserId author) {
        enqueue(
                viewer,
                NotificationKind.TICKET_CREATED,
                ticket,
                ticket.requester().id(),
                author,
                body);
        assigned(viewer, null, ticket, author);
    }

    /** 公開コメントを、依頼者が書いたら担当者に、それ以外が書いたら依頼者に知らせる（F-N-2）。 */
    void commented(Viewer viewer, Ticket ticket, UserId author, String body, boolean isPublic) {
        if (!isPublic) {
            return;
        }
        var byRequester = ticket.requester().id().equals(author.value());
        var recipient =
                byRequester ? idOf(ticket.assignee()) : ticket.requester().id();
        if (recipient != null) {
            send(viewer, NotificationKind.PUBLIC_COMMENT, ticket, recipient, author, body);
        }
    }

    /** 担当者が変わったら、新しい担当者に知らせる（F-N-3）。 */
    void assigned(Viewer viewer, @Nullable Ticket before, Ticket after, UserId author) {
        var assignee = idOf(after.assignee());
        if (assignee != null && !Objects.equals(assignee, before == null ? null : idOf(before.assignee()))) {
            send(viewer, NotificationKind.ASSIGNED, after, assignee, author, "");
        }
    }

    private void send(
            Viewer viewer,
            NotificationKind kind,
            Ticket ticket,
            java.util.UUID recipientId,
            UserId author,
            String body) {
        if (!recipientId.equals(author.value())) {
            enqueue(viewer, kind, ticket, recipientId, author, body);
        }
    }

    private void enqueue(
            Viewer viewer,
            NotificationKind kind,
            Ticket ticket,
            java.util.UUID recipientId,
            UserId author,
            String body) {
        var recipient =
                directory.user(viewer, recipientId).filter(UserAccount::active).orElse(null);
        if (recipient == null) {
            return;
        }
        var authorName =
                directory.user(viewer, author.value()).map(UserAccount::name).orElse("");
        var tenant = tenants.current(viewer);
        var path = recipient.role() == Role.REQUESTER ? "/requests/" : "/agent/tickets/";
        notifications.enqueue(
                viewer,
                kind,
                recipient.id(),
                Map.of(
                        "to", recipient.email(),
                        "tenantName", tenant.name(),
                        "url", baseUrl + "/t/" + tenant.slug() + path + ticket.number(),
                        "number", String.valueOf(ticket.number()),
                        "subject", ticket.subject(),
                        "body", body,
                        "author", authorName));
    }

    private static java.util.@Nullable UUID idOf(@Nullable Ref ref) {
        return ref == null ? null : ref.id();
    }
}
