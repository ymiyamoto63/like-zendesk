package dev.likezendesk.ticket.app;

import dev.likezendesk.identity.app.Directory;
import dev.likezendesk.shared.ApiException;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.ticket.domain.Actor;
import dev.likezendesk.ticket.domain.Comment;
import dev.likezendesk.ticket.domain.Priority;
import dev.likezendesk.ticket.domain.Ref;
import dev.likezendesk.ticket.domain.Ticket;
import dev.likezendesk.ticket.domain.TicketPage;
import dev.likezendesk.ticket.domain.TicketStatus;
import dev.likezendesk.ticket.domain.TicketType;
import dev.likezendesk.ticket.infra.CommentRepository;
import dev.likezendesk.ticket.infra.TicketRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 依頼者ポータルのチケット（F-P-1〜4）。依頼者は自分のチケットの公開情報だけを扱える。 */
@Service
public class Requests {

    private final Tickets tickets;
    private final TicketRepository repository;
    private final CommentRepository comments;
    private final Directory directory;
    private final Clock clock;

    Requests(
            Tickets tickets,
            TicketRepository repository,
            CommentRepository comments,
            Directory directory,
            Clock clock) {
        this.tickets = tickets;
        this.repository = repository;
        this.comments = comments;
        this.directory = directory;
        this.clock = clock;
    }

    /** 返信の結果。クローズ済みのチケットへの返信なら、{@code ticket} は新しく起票したチケット。 */
    public record Replied(Comment comment, Ticket ticket, boolean followUp) {}

    @Transactional(readOnly = true)
    public TicketPage list(Viewer.Requester viewer, int page, int size) {
        return repository.listOwn(viewer, page, size);
    }

    @Transactional(readOnly = true)
    public Ticket get(Viewer.Requester viewer, long number) {
        return repository.find(viewer, number).orElseThrow(ApiException::notFound);
    }

    /** 公開コメントだけ（CommentVisibility が絞る）。 */
    @Transactional(readOnly = true)
    public List<Comment> comments(Viewer.Requester viewer, long number) {
        return comments.list(viewer, get(viewer, number).id());
    }

    /** 起票（F-P-1）。優先度は通常、種別は質問で始め、振り分けはエージェントが行う。 */
    @Transactional
    public Ticket create(Viewer.Requester viewer, String subject, String body) {
        return tickets.insert(
                viewer,
                self(viewer),
                subject,
                body,
                TicketStatus.NEW,
                Priority.NORMAL,
                TicketType.QUESTION,
                null,
                null,
                List.of(),
                null);
    }

    /**
     * 返信（F-P-3）。保留・解決済みならオープンに戻す。クローズ済みなら、返信を本文にした新しいチケットを起票し、元のチケットにつなぐ（要件 7）。
     */
    @Transactional
    public Replied reply(Viewer.Requester viewer, long number, String body) {
        var ticket = repository.findForUpdate(viewer, number).orElseThrow(ApiException::notFound);
        if (ticket.status() == TicketStatus.CLOSED) {
            var followUp = tickets.insert(
                    viewer,
                    self(viewer),
                    ticket.subject(),
                    body,
                    TicketStatus.NEW,
                    ticket.priority(),
                    ticket.type(),
                    null,
                    null,
                    ticket.tags(),
                    ticket);
            var comment = comments.list(viewer, followUp.id()).getFirst();
            return new Replied(comment, followUp, true);
        }
        var actor = new Actor.Requester(viewer.userId());
        var after = ticket.apply(Ticket.Update.statusOnly(TicketStatus.OPEN), actor, clock.instant());
        var commented = tickets.addComment(viewer, ticket, after, body, true);
        return new Replied(commented.comment(), commented.ticket(), false);
    }

    /** 解決済みにする（F-P-4）。担当者のいないチケットは解決にできない（422 {@code status.solved_requires_assignee}）。 */
    @Transactional
    public Ticket solve(Viewer.Requester viewer, long number) {
        var ticket = repository.findForUpdate(viewer, number).orElseThrow(ApiException::notFound);
        var actor = new Actor.Requester(viewer.userId());
        var applied = ticket.apply(Ticket.Update.statusOnly(TicketStatus.SOLVED), actor, clock.instant());
        if (!applied.changes().isEmpty()) {
            tickets.save(viewer, ticket, applied.ticket(), applied.changes());
        }
        return applied.ticket();
    }

    private Ref self(Viewer.Requester viewer) {
        var me = directory.user(viewer, viewer.userId().value()).orElseThrow(ApiException::notFound);
        return new Ref(me.id().value(), me.name());
    }
}
