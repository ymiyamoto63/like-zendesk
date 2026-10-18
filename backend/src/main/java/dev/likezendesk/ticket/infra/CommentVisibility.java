package dev.likezendesk.ticket.infra;

import dev.likezendesk.shared.SqlCondition;
import dev.likezendesk.shared.Viewer;
import java.util.Map;

/** 閲覧者に見えるコメントの条件。親チケットが見えること、依頼者には公開コメントだけであること。 */
final class CommentVisibility {

    private CommentVisibility() {}

    /** {@code commentAlias} はコメント表、{@code ticketAlias} は結合したチケット表の別名。 */
    static SqlCondition of(Viewer viewer, String commentAlias, String ticketAlias) {
        var ticket = TicketVisibility.of(viewer, ticketAlias);
        return switch (viewer) {
            case Viewer.Requester _ -> ticket.and(new SqlCondition(commentAlias + ".public = true", Map.of()));
            case Viewer.Staff _, Viewer.System _ -> ticket;
        };
    }
}
