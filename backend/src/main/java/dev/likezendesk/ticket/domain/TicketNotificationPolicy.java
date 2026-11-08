package dev.likezendesk.ticket.domain;

import dev.likezendesk.notification.domain.NotificationKind;
import dev.likezendesk.shared.UserId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * チケットの出来事から、誰にどの通知を送るかを決める（F-N-1〜3、F-N-5）。Spring にも DB にも依存しない。
 *
 * <p>受付メールを除き、自分の操作を自分に知らせることはしない。社内メモは誰にも送らない。無効にした利用者を除くのは、送り先を引く側の仕事。
 */
public final class TicketNotificationPolicy {

    /** 送るべき通知 1 件。{@code body} は本文やコメント（なければ空）。 */
    public record Intent(NotificationKind kind, UUID recipientId, String body) {}

    private TicketNotificationPolicy() {}

    /** 受付メールを依頼者に（F-N-1）。依頼者が自分で起票しても送る。作成時に担当者がいれば、その人にも知らせる（F-N-3）。 */
    public static List<Intent> created(Ticket ticket, String body, UserId author) {
        var intents = new ArrayList<Intent>();
        intents.add(
                new Intent(NotificationKind.TICKET_CREATED, ticket.requester().id(), body));
        intents.addAll(assigned(null, ticket, author));
        return intents;
    }

    /** 公開コメントを、依頼者が書いたら担当者に、それ以外が書いたら依頼者に知らせる（F-N-2）。 */
    public static List<Intent> commented(Ticket ticket, UserId author, String body, boolean isPublic) {
        if (!isPublic) {
            return List.of();
        }
        var byRequester = ticket.requester().id().equals(author.value());
        var recipient =
                byRequester ? idOf(ticket.assignee()) : ticket.requester().id();
        return to(NotificationKind.PUBLIC_COMMENT, recipient, author, body);
    }

    /** 担当者が変わったら、新しい担当者に知らせる（F-N-3）。 */
    public static List<Intent> assigned(@Nullable Ticket before, Ticket after, UserId author) {
        var assignee = idOf(after.assignee());
        if (assignee == null || Objects.equals(assignee, before == null ? null : idOf(before.assignee()))) {
            return List.of();
        }
        return to(NotificationKind.ASSIGNED, assignee, author, "");
    }

    /** 依頼者が解決済みにしたら、担当者に知らせる（F-N-5）。 */
    public static List<Intent> solved(Ticket ticket, UserId author) {
        return to(NotificationKind.SOLVED, idOf(ticket.assignee()), author, "");
    }

    private static List<Intent> to(NotificationKind kind, @Nullable UUID recipient, UserId author, String body) {
        if (recipient == null || recipient.equals(author.value())) {
            return List.of();
        }
        return List.of(new Intent(kind, recipient, body));
    }

    private static @Nullable UUID idOf(@Nullable Ref ref) {
        return ref == null ? null : ref.id();
    }
}
