package dev.likezendesk.ticket.infra;

import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.ticket.domain.Attachment;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 添付（F-T-7）。コメントに紐付くまではアップロードした本人だけが読め、紐付いた後は親コメントの可視性に従う（architecture.md 2.9）。
 */
@Repository
public class AttachmentRepository {

    private static final String COLUMNS = "a.id, a.filename, a.content_type, a.size, a.storage_key";

    private final JdbcClient jdbc;

    AttachmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insert(
            Viewer viewer, UserId uploader, String filename, String contentType, long size, String key, Instant now) {
        return jdbc.sql("""
                        INSERT INTO attachments
                          (tenant_id, uploader_id, filename, content_type, size, storage_key, created_at)
                        VALUES (:v_tenant, :uploader, :filename, :type, :size, :key, :now)
                        RETURNING id
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .param("uploader", uploader.value())
                .param("filename", filename)
                .param("type", contentType)
                .param("size", size)
                .param("key", key)
                .param("now", Timestamp.from(now))
                .query(UUID.class)
                .single();
    }

    /** 閲覧者が読める添付。未紐付けなら本人の分だけ。 */
    public Optional<Attachment> find(Viewer viewer, UUID id) {
        var visible = CommentVisibility.of(viewer, "c", "t");
        var own = viewer instanceof Viewer.Staff s
                ? s.userId().value()
                : viewer instanceof Viewer.Requester r ? r.userId().value() : null;
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM attachments a
                        LEFT JOIN comments c ON c.tenant_id = a.tenant_id AND c.id = a.comment_id
                        LEFT JOIN tickets t ON t.tenant_id = c.tenant_id AND t.id = c.ticket_id
                        WHERE a.tenant_id = :v_tenant AND a.id = :id
                          AND ((a.comment_id IS NULL AND a.uploader_id = CAST(:own AS uuid))
                               OR (a.comment_id IS NOT NULL AND %s))
                        """.formatted(visible.sql()))
                .params(visible.params())
                .param("v_tenant", viewer.tenantId().value())
                .param("id", id)
                .param("own", own)
                .query(AttachmentRepository::map)
                .optional();
    }

    /**
     * 本人がアップロードした未紐付けの添付を、コメントに紐付ける。紐付けた件数を返す（一部でも他人のものや紐付け済みなら、呼び出し側がロールバックする）。
     */
    public int attach(Viewer viewer, UserId uploader, List<UUID> ids, UUID commentId) {
        return jdbc.sql("""
                        UPDATE attachments SET comment_id = :comment
                        WHERE tenant_id = :v_tenant AND id = ANY (:ids)
                          AND comment_id IS NULL AND uploader_id = :uploader
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .param("comment", commentId)
                .param("ids", ids.toArray(UUID[]::new))
                .param("uploader", uploader.value())
                .update();
    }

    public record Owned(UUID commentId, Attachment attachment) {}

    /** チケットの全コメントの添付。コメントの可視性は呼び出し側（コメント一覧）で絞る。 */
    public List<Owned> listForTicket(Viewer viewer, UUID ticketId) {
        return jdbc.sql("SELECT a.comment_id, " + COLUMNS + """
                         FROM attachments a
                        JOIN comments c ON c.tenant_id = a.tenant_id AND c.id = a.comment_id
                        WHERE a.tenant_id = :v_tenant AND c.ticket_id = :ticket
                        ORDER BY a.created_at, a.id
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .param("ticket", ticketId)
                .query((rs, row) -> new Owned(rs.getObject("comment_id", UUID.class), map(rs, row)))
                .list();
    }

    /** コメントに紐付かないまま {@code before} より前にアップロードされた添付を消し、その保存キーを返す。 */
    public List<String> deleteUnattached(Viewer.System viewer, Instant before) {
        return jdbc.sql("""
                        DELETE FROM attachments
                        WHERE tenant_id = :v_tenant AND comment_id IS NULL AND created_at < :before
                        RETURNING storage_key
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .param("before", Timestamp.from(before))
                .query(String.class)
                .list();
    }

    private static Attachment map(ResultSet rs, int row) throws SQLException {
        return new Attachment(
                rs.getObject("id", UUID.class),
                rs.getString("filename"),
                rs.getString("content_type"),
                rs.getLong("size"),
                rs.getString("storage_key"));
    }
}
