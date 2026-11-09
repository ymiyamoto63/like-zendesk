package dev.likezendesk.ticket.app;

import dev.likezendesk.identity.app.Directory;
import dev.likezendesk.shared.ApiException;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.ticket.domain.Actor;
import dev.likezendesk.ticket.domain.ChangeSet;
import dev.likezendesk.ticket.domain.Comment;
import dev.likezendesk.ticket.domain.Patch;
import dev.likezendesk.ticket.domain.Priority;
import dev.likezendesk.ticket.domain.Ref;
import dev.likezendesk.ticket.domain.Ticket;
import dev.likezendesk.ticket.domain.Ticket.FieldChange;
import dev.likezendesk.ticket.domain.TicketFilter;
import dev.likezendesk.ticket.domain.TicketPage;
import dev.likezendesk.ticket.domain.TicketStatus;
import dev.likezendesk.ticket.domain.TicketStatusRules;
import dev.likezendesk.ticket.domain.TicketType;
import dev.likezendesk.ticket.infra.CommentRepository;
import dev.likezendesk.ticket.infra.TicketEventRepository;
import dev.likezendesk.ticket.infra.TicketRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * チケットの作成・参照・更新とコメント。スタッフの操作（F-T、F-C、F-L）と依頼者ポータルの操作（F-P）を 1 つのインターフェースに持つ。
 *
 * <p>依頼者は自分のチケットの公開情報だけを扱える。閲覧者の型で受け付ける操作を分け、書き込みは必ず {@link #insert} /
 * {@link #addComment} / {@link #save} を通るので、監査ログと通知の規則はここに集まる。
 */
@Service
public class Tickets {

    private final TicketRepository tickets;
    private final CommentRepository comments;
    private final TicketEventRepository events;
    private final Directory directory;
    private final TicketNotifications notifications;
    private final Attachments attachments;
    private final Clock clock;

    Tickets(
            TicketRepository tickets,
            CommentRepository comments,
            TicketEventRepository events,
            Directory directory,
            TicketNotifications notifications,
            Attachments attachments,
            Clock clock) {
        this.tickets = tickets;
        this.comments = comments;
        this.events = events;
        this.directory = directory;
        this.notifications = notifications;
        this.attachments = attachments;
        this.clock = clock;
    }

    public record NewTicket(
            UUID requesterId,
            String subject,
            String body,
            Priority priority,
            TicketType type,
            @Nullable UUID groupId,
            @Nullable UUID assigneeId,
            List<String> tags,
            List<UUID> attachmentIds) {}

    /** 項目の変更。{@code groupId} と {@code assigneeId} は、外す（null にする）ことと変えないことを区別する。 */
    public record Changes(
            int version,
            @Nullable String subject,
            @Nullable TicketStatus status,
            @Nullable Priority priority,
            @Nullable TicketType type,
            Patch<UUID> groupId,
            Patch<UUID> assigneeId,
            @Nullable List<String> tags) {}

    public record Commented(Comment comment, Ticket ticket) {}

    /** 依頼者を指定して作る（F-T-1）。本文は依頼者に見える最初のコメントにする。 */
    @Transactional
    public Ticket create(Viewer.Staff viewer, NewTicket command) {
        var requester = directory
                .user(viewer, command.requesterId())
                .filter(u -> u.active())
                .orElseThrow(() -> ApiException.rule("ticket.requester_not_found", "Requester not found"));
        var group = resolveGroup(viewer, command.groupId());
        var assignee = resolveAssignee(viewer, command.assigneeId());
        checkAssignment(viewer, group, assignee);
        var status = switch (TicketStatusRules.next(
                TicketStatus.NEW, null, assignee != null, new Actor.Staff(viewer.userId()))) {
            case TicketStatusRules.Result.Ok(var s) -> s;
            case TicketStatusRules.Result.Rejected(var code) -> throw ApiException.rule(code, "Invalid status");
        };
        return insert(
                viewer,
                new Draft(
                        new Ref(requester.id().value(), requester.name()),
                        command.subject(),
                        command.body(),
                        command.attachmentIds(),
                        status,
                        command.priority(),
                        command.type(),
                        group,
                        assignee,
                        Ticket.normalizeTags(command.tags()),
                        null));
    }

    /** 起票するチケットの中身。スタッフの起票、依頼者の起票、クローズ後の返信からの起票で共有する。 */
    private record Draft(
            Ref requester,
            String subject,
            String body,
            List<UUID> attachmentIds,
            TicketStatus status,
            Priority priority,
            TicketType type,
            @Nullable Ref group,
            @Nullable Ref assignee,
            List<String> tags,
            @Nullable Ticket followUpOf) {}

    /**
     * チケットと最初の公開コメント（本文）を書き、監査ログと通知を残す。作成者は閲覧者本人。スタッフの起票、依頼者の起票、クローズ後の返信からの起票で共有する。
     */
    private Ticket insert(Viewer viewer, Draft draft) {
        var requester = draft.requester();
        var subject = draft.subject();
        var body = draft.body();
        var attachmentIds = draft.attachmentIds();
        var status = draft.status();
        var priority = draft.priority();
        var type = draft.type();
        var group = draft.group();
        var assignee = draft.assignee();
        var tags = draft.tags();
        var followUpOf = draft.followUpOf();
        var author = authorOf(viewer);
        var now = clock.instant();
        var number = tickets.nextNumber(viewer);
        var id = tickets.insert(
                viewer,
                number,
                new TicketRepository.NewTicket(
                        subject.strip(),
                        requester.id(),
                        status,
                        priority,
                        type,
                        group == null ? null : group.id(),
                        assignee == null ? null : assignee.id(),
                        tags,
                        followUpOf == null ? null : followUpOf.id()),
                now);
        var commentId = comments.insert(viewer, id, author, body, true, now).orElseThrow(ApiException::notFound);
        attachments.attach(viewer, attachmentIds, commentId);
        var created = new ArrayList<FieldChange>();
        var summary = new java.util.LinkedHashMap<String, Object>();
        summary.put("number", number);
        summary.put("subject", subject.strip());
        if (followUpOf != null) {
            summary.put("followUpOf", followUpOf.number());
        }
        created.add(new FieldChange("created", null, summary));
        created.add(new FieldChange("status", null, status.dbValue()));
        created.add(new FieldChange("priority", null, priority.dbValue()));
        created.add(new FieldChange("type", null, type.dbValue()));
        if (group != null) {
            created.add(new FieldChange("group", null, Ticket.refValue(group)));
        }
        if (assignee != null) {
            created.add(new FieldChange("assignee", null, Ticket.refValue(assignee)));
        }
        if (!tags.isEmpty()) {
            created.add(new FieldChange("tags", null, tags));
        }
        created.add(commentEvent(commentId, true));
        events.insert(viewer, id, UUID.randomUUID(), author, created, now);
        var ticket = tickets.find(viewer, number).orElseThrow();
        notifications.created(viewer, ticket, body, author);
        return ticket;
    }

    private static UserId authorOf(Viewer viewer) {
        return switch (viewer) {
            case Viewer.Staff s -> s.userId();
            case Viewer.Requester r -> r.userId();
            case Viewer.System _ -> throw new IllegalArgumentException("The system does not write tickets");
        };
    }

    @Transactional(readOnly = true)
    public Ticket get(Viewer.Staff viewer, long number) {
        return tickets.find(viewer, number).orElseThrow(ApiException::notFound);
    }

    /** 条件と全文検索に合うチケットの 1 ページ（F-V-1〜4）。条件は呼び出し側で検証済みであること。 */
    @Transactional(readOnly = true)
    public TicketPage search(Viewer.Staff viewer, TicketFilter filter, String query, int page, int size) {
        return tickets.search(viewer, filter, query, page, size, clock.instant());
    }

    /** ビューの件数（F-V-5）。 */
    @Transactional(readOnly = true)
    public long count(Viewer.Staff viewer, TicketFilter filter) {
        return tickets.count(viewer, filter, "", clock.instant());
    }

    /** 項目を変える（F-T-5、F-T-6）。他の人が先に更新していたら 409。 */
    @Transactional
    public Ticket update(Viewer.Staff viewer, long number, Changes changes) {
        var ticket = lock(viewer, number, changes.version());
        var applied = apply(viewer, ticket, changes);
        if (!applied.changes().isEmpty()) {
            save(viewer, ticket, applied.ticket(), applied.changes());
            notifications.assigned(viewer, ticket, applied.ticket(), viewer.userId());
        }
        return applied.ticket();
    }

    /** コメントを追加する（F-C-1）。{@code changes} があれば、同じ変更として項目も変える（F-C-3）。 */
    @Transactional
    public Commented comment(
            Viewer.Staff viewer,
            long number,
            String body,
            boolean isPublic,
            List<UUID> attachmentIds,
            @Nullable Changes changes) {
        var ticket = changes == null ? lockAny(viewer, number) : lock(viewer, number, changes.version());
        if (ticket.status() == TicketStatus.CLOSED) {
            throw ApiException.rule("status.closed_is_final", "Closed tickets cannot be changed");
        }
        var now = clock.instant();
        var after = changes == null
                ? ticket.apply(Ticket.Update.statusOnly(null), actor(viewer), now)
                : apply(viewer, ticket, changes);
        return addComment(viewer, ticket, after, body, isPublic, attachmentIds);
    }

    /** ロック済みの {@code before} に、変更 {@code after} とコメントを 1 回の変更として書き、通知する。 */
    private Commented addComment(
            Viewer viewer,
            Ticket before,
            Ticket.Applied after,
            String body,
            boolean isPublic,
            List<UUID> attachmentIds) {
        var author = authorOf(viewer);
        var now = clock.instant();
        var commentId = comments.insert(viewer, before.id(), author, body, isPublic, now)
                .orElseThrow(ApiException::notFound);
        attachments.attach(viewer, attachmentIds, commentId);
        var all = new ArrayList<>(after.changes());
        all.add(commentEvent(commentId, isPublic));
        var touched = touch(after.ticket(), before, now);
        save(viewer, before, touched, all);
        notifications.assigned(viewer, before, touched, author);
        notifications.commented(viewer, touched, author, body, isPublic);
        var comment = comments.list(viewer, before.id()).stream()
                .filter(c -> c.id().equals(commentId))
                .findFirst()
                .orElseThrow();
        return new Commented(comment, touched);
    }

    @Transactional(readOnly = true)
    public List<Comment> comments(Viewer.Staff viewer, long number) {
        return comments.list(viewer, get(viewer, number).id());
    }

    @Transactional(readOnly = true)
    public List<ChangeSet> events(Viewer.Staff viewer, long number) {
        return events.list(viewer, get(viewer, number).id());
    }

    // ---- 依頼者ポータル（F-P-1〜4）。依頼者は自分のチケットの公開情報だけを扱える ----

    /** 返信の結果。クローズ済みのチケットへの返信なら、{@code ticket} は新しく起票したチケット。 */
    public record Replied(Comment comment, Ticket ticket, boolean followUp) {}

    @Transactional(readOnly = true)
    public TicketPage list(Viewer.Requester viewer, int page, int size) {
        return tickets.listOwn(viewer, page, size);
    }

    @Transactional(readOnly = true)
    public Ticket get(Viewer.Requester viewer, long number) {
        return tickets.find(viewer, number).orElseThrow(ApiException::notFound);
    }

    /** 公開コメントだけ（CommentVisibility が絞る）。 */
    @Transactional(readOnly = true)
    public List<Comment> comments(Viewer.Requester viewer, long number) {
        return comments.list(viewer, get(viewer, number).id());
    }

    /** 起票（F-P-1）。優先度は通常、種別は質問で始め、振り分けはエージェントが行う。 */
    @Transactional
    public Ticket create(Viewer.Requester viewer, String subject, String body, List<UUID> attachmentIds) {
        return insert(
                viewer,
                new Draft(
                        self(viewer),
                        subject,
                        body,
                        attachmentIds,
                        TicketStatus.NEW,
                        Priority.NORMAL,
                        TicketType.QUESTION,
                        null,
                        null,
                        List.of(),
                        null));
    }

    /**
     * 返信（F-P-3）。保留・解決済みならオープンに戻す。クローズ済みなら、返信を本文にした新しいチケットを起票し、元のチケットにつなぐ（要件 7）。
     */
    @Transactional
    public Replied reply(Viewer.Requester viewer, long number, String body, List<UUID> attachmentIds) {
        var ticket = tickets.findForUpdate(viewer, number).orElseThrow(ApiException::notFound);
        if (ticket.status() == TicketStatus.CLOSED) {
            var followUp = insert(
                    viewer,
                    new Draft(
                            self(viewer),
                            ticket.subject(),
                            body,
                            attachmentIds,
                            TicketStatus.NEW,
                            ticket.priority(),
                            ticket.type(),
                            null,
                            null,
                            ticket.tags(),
                            ticket));
            var comment = comments.list(viewer, followUp.id()).getFirst();
            return new Replied(comment, followUp, true);
        }
        var actor = new Actor.Requester(viewer.userId());
        var after = ticket.apply(Ticket.Update.statusOnly(TicketStatus.OPEN), actor, clock.instant());
        var commented = addComment(viewer, ticket, after, body, true, attachmentIds);
        return new Replied(commented.comment(), commented.ticket(), false);
    }

    /**
     * 解決済みにする（F-P-4）。担当者のいないチケットは解決にできない（422 {@code status.solved_requires_assignee}）。
     * 担当者には解決したことをメールで知らせる（F-N-5）。
     */
    @Transactional
    public Ticket solve(Viewer.Requester viewer, long number) {
        var ticket = tickets.findForUpdate(viewer, number).orElseThrow(ApiException::notFound);
        var actor = new Actor.Requester(viewer.userId());
        var applied = ticket.apply(Ticket.Update.statusOnly(TicketStatus.SOLVED), actor, clock.instant());
        if (!applied.changes().isEmpty()) {
            save(viewer, ticket, applied.ticket(), applied.changes());
            notifications.solved(viewer, applied.ticket(), viewer.userId());
        }
        return applied.ticket();
    }

    private Ref self(Viewer.Requester viewer) {
        var me = directory.user(viewer, viewer.userId().value()).orElseThrow(ApiException::notFound);
        return new Ref(me.id().value(), me.name());
    }

    private Ticket lockAny(Viewer.Staff viewer, long number) {
        return tickets.findForUpdate(viewer, number).orElseThrow(ApiException::notFound);
    }

    private Ticket lock(Viewer.Staff viewer, long number, int version) {
        var ticket = lockAny(viewer, number);
        if (ticket.version() != version) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ticket.version_conflict",
                    "The ticket was updated by someone else",
                    Map.of("currentVersion", ticket.version()));
        }
        return ticket;
    }

    private Ticket.Applied apply(Viewer.Staff viewer, Ticket ticket, Changes changes) {
        var update = new Ticket.Update(
                changes.subject() == null ? null : changes.subject().strip(),
                changes.status(),
                changes.priority(),
                changes.type(),
                resolve(changes.groupId(), id -> resolveGroup(viewer, id)),
                resolve(changes.assigneeId(), id -> resolveAssignee(viewer, id)),
                changes.tags());
        var applied = ticket.apply(update, actor(viewer), clock.instant());
        checkAssignment(viewer, applied.ticket().group(), applied.ticket().assignee());
        return applied;
    }

    /** ロック済みのチケットを書き、監査ログを残す。 */
    private void save(Viewer viewer, Ticket before, Ticket after, List<FieldChange> changes) {
        if (!tickets.update(viewer, after, before.version())) {
            // 行をロックして版を確かめた後なので、ここには来ない
            throw new IllegalStateException("Ticket " + before.number() + " changed while locked");
        }
        events.insert(viewer, before.id(), UUID.randomUUID(), authorOf(viewer), changes, after.updatedAt());
    }

    /** 項目が変わらなくても、コメントの追加で更新日時と版を進める。 */
    private static Ticket touch(Ticket after, Ticket before, java.time.Instant now) {
        if (after.version() != before.version()) {
            return after;
        }
        return new Ticket(
                after.id(),
                after.number(),
                after.subject(),
                after.requester(),
                after.status(),
                after.priority(),
                after.type(),
                after.group(),
                after.assignee(),
                after.tags(),
                after.followUpOf(),
                after.version() + 1,
                after.createdAt(),
                now,
                after.solvedAt(),
                after.closedAt());
    }

    private static Actor actor(Viewer.Staff viewer) {
        return new Actor.Staff(viewer.userId());
    }

    private static FieldChange commentEvent(UUID commentId, boolean isPublic) {
        return new FieldChange("comment", null, Map.of("id", commentId.toString(), "public", isPublic));
    }

    private static Patch<Ref> resolve(Patch<UUID> patch, java.util.function.Function<UUID, @Nullable Ref> resolver) {
        return switch (patch) {
            case Patch.Keep<UUID> _ -> Patch.keep();
            case Patch.Set<UUID>(var id) -> Patch.set(id == null ? null : resolver.apply(id));
        };
    }

    private @Nullable Ref resolveGroup(Viewer.Staff viewer, @Nullable UUID id) {
        if (id == null) {
            return null;
        }
        return directory
                .group(viewer, id)
                .map(g -> new Ref(g.id(), g.name()))
                .orElseThrow(() -> ApiException.rule("group.not_found", "Group not found"));
    }

    private @Nullable Ref resolveAssignee(Viewer.Staff viewer, @Nullable UUID id) {
        if (id == null) {
            return null;
        }
        return directory
                .user(viewer, id)
                .filter(u -> u.active())
                .map(u -> new Ref(u.id().value(), u.name()))
                .orElseThrow(() -> ApiException.rule("assignee.not_found", "Assignee not found"));
    }

    /** 担当者は担当グループに所属していること（F-T-6）。 */
    private void checkAssignment(Viewer.Staff viewer, @Nullable Ref group, @Nullable Ref assignee) {
        if (assignee == null) {
            return;
        }
        if (group == null) {
            throw ApiException.rule("assignee.requires_group", "An assignee needs a group");
        }
        if (!directory.isMember(viewer, group.id(), assignee.id())) {
            throw ApiException.rule("assignee.not_in_group", "The assignee is not a member of the group");
        }
    }
}
