package dev.likezendesk.ticket.app;

import dev.likezendesk.identity.app.Directory;
import dev.likezendesk.identity.domain.Role;
import dev.likezendesk.identity.domain.UserAccount;
import dev.likezendesk.notification.app.Notifications;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.tenant.app.Tenants;
import dev.likezendesk.ticket.domain.Ticket;
import dev.likezendesk.ticket.domain.TicketNotificationPolicy;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * チケットのメール通知（F-N-1〜3、F-N-5）。誰に何を送るかは {@link TicketNotificationPolicy} が決め、ここは送り先を引いて、チケットの更新と同じ
 * トランザクションで Outbox に書く。無効にした利用者には送らない。
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

    void created(Viewer viewer, Ticket ticket, String body, UserId author) {
        deliver(viewer, ticket, author, TicketNotificationPolicy.created(ticket, body, author));
    }

    void commented(Viewer viewer, Ticket ticket, UserId author, String body, boolean isPublic) {
        deliver(viewer, ticket, author, TicketNotificationPolicy.commented(ticket, author, body, isPublic));
    }

    void assigned(Viewer viewer, @Nullable Ticket before, Ticket after, UserId author) {
        deliver(viewer, after, author, TicketNotificationPolicy.assigned(before, after, author));
    }

    void solved(Viewer viewer, Ticket ticket, UserId author) {
        deliver(viewer, ticket, author, TicketNotificationPolicy.solved(ticket, author));
    }

    /** 宛先と文面は、依頼者の操作でも担当者の宛先を引けるよう、システムとして読む。テナントと書いた人は 1 回だけ引く。 */
    private void deliver(Viewer actor, Ticket ticket, UserId author, List<TicketNotificationPolicy.Intent> intents) {
        if (intents.isEmpty()) {
            return;
        }
        var viewer = new Viewer.System(actor.tenantId());
        var authorName =
                directory.user(viewer, author.value()).map(UserAccount::name).orElse("");
        var tenant = tenants.current(viewer);
        for (var intent : intents) {
            var recipient = directory
                    .user(viewer, intent.recipientId())
                    .filter(UserAccount::active)
                    .orElse(null);
            if (recipient == null) {
                continue;
            }
            var path = recipient.role() == Role.REQUESTER ? "/requests/" : "/agent/tickets/";
            notifications.enqueue(
                    viewer,
                    intent.kind(),
                    recipient.id(),
                    Map.of(
                            "to", recipient.email(),
                            "tenantName", tenant.name(),
                            "url", baseUrl + "/t/" + tenant.slug() + path + ticket.number(),
                            "number", String.valueOf(ticket.number()),
                            "subject", ticket.subject(),
                            "body", intent.body(),
                            "author", authorName));
        }
    }
}
