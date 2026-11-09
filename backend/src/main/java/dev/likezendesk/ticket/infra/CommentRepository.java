package dev.likezendesk.ticket.infra;

import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.ticket.domain.Comment;
import dev.likezendesk.ticket.domain.Ref;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** コメントは追記のみ（F-C-2）。更新・削除のメソッドは作らない（DB の権限でも禁止している）。 */
@Repository
public class CommentRepository {

    private final JdbcClient jdbc;

    private final AttachmentRepository attachments;

    CommentRepository(JdbcClient jdbc, AttachmentRepository attachments) {
        this.jdbc = jdbc;
        this.attachments = attachments;
    }

    /**
     * コメントを書く。{@code ticketId} のチケットが閲覧者に見えなければ書かずに空を返す（呼び出し側の確認を当てにしない）。依頼者は公開コメントしか書けない。
     */
    public Optional<UUID> insert(
            Viewer viewer, UUID ticketId, UserId author, String body, boolean isPublic, Instant now) {
        if (!isPublic && viewer instanceof Viewer.Requester) {
            throw new IllegalArgumentException("A requester cannot write an internal note");
        }
        return TicketScope.statement(jdbc, viewer, """
                        INSERT INTO comments (tenant_id, ticket_id, author_id, body, public, created_at)
                        SELECT t.tenant_id, t.id, :author, :body, :public, :now FROM tickets t
                        """, " AND t.id = :ticket RETURNING id")
                .param("ticket", ticketId)
                .param("author", author.value())
                .param("body", body)
                .param("public", isPublic)
                .param("now", Timestamp.from(now))
                .query(UUID.class)
                .optional();
    }

    /** 古い順。依頼者には公開コメントだけを返す。添付つき。 */
    public List<Comment> list(Viewer viewer, UUID ticketId) {
        var files = attachments.listForTicket(viewer, ticketId).stream()
                .collect(Collectors.groupingBy(
                        AttachmentRepository.Owned::commentId,
                        Collectors.mapping(AttachmentRepository.Owned::attachment, Collectors.toList())));
        var visible = CommentVisibility.of(viewer, "c", "t");
        return jdbc.sql("""
                        SELECT c.id, c.body, c.public, c.created_at, u.id AS author_id, u.name AS author_name
                        FROM comments c
                        JOIN tickets t ON t.tenant_id = c.tenant_id AND t.id = c.ticket_id
                        JOIN users u ON u.tenant_id = c.tenant_id AND u.id = c.author_id
                        WHERE %s AND c.ticket_id = :ticket
                        ORDER BY c.created_at, c.id
                        """.formatted(visible.sql()))
                .params(visible.params())
                .param("ticket", ticketId)
                .query((rs, row) -> new Comment(
                        rs.getObject("id", UUID.class),
                        new Ref(rs.getObject("author_id", UUID.class), rs.getString("author_name")),
                        rs.getString("body"),
                        rs.getBoolean("public"),
                        rs.getTimestamp("created_at").toInstant(),
                        files.getOrDefault(rs.getObject("id", UUID.class), List.of())))
                .list();
    }
}
