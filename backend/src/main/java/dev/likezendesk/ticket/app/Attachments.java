package dev.likezendesk.ticket.app;

import dev.likezendesk.shared.ApiException;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.tenant.app.Tenants;
import dev.likezendesk.ticket.domain.Attachment;
import dev.likezendesk.ticket.infra.AttachmentRepository;
import dev.likezendesk.ticket.infra.BlobStore;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 添付のアップロードとダウンロード（F-T-7）。アップロードは 2 段階で、ここで ID を返し、コメントを書くときに紐付ける（architecture.md 2.9）。
 */
@Service
public class Attachments {

    private static final Logger log = LoggerFactory.getLogger(Attachments.class);

    /** コメントに紐付かないまま、この時間がたった添付は消す。 */
    static final Duration UNATTACHED_TTL = Duration.ofHours(24);

    private final AttachmentRepository attachments;
    private final BlobStore blobs;
    private final Tenants tenants;
    private final Clock clock;

    Attachments(AttachmentRepository attachments, BlobStore blobs, Tenants tenants, Clock clock) {
        this.attachments = attachments;
        this.blobs = blobs;
        this.tenants = tenants;
        this.clock = clock;
    }

    public record Download(Attachment attachment, InputStream content) {}

    /** 中身を保存してから行を書く。行の書き込みに失敗したら中身を消す。 */
    @Transactional
    public Attachment upload(
            Viewer viewer, @Nullable String filename, @Nullable String contentType, InputStream content) {
        var uploader = uploaderOf(viewer);
        var key = UUID.randomUUID().toString();
        long size;
        try {
            size = blobs.put(key, content, Attachment.MAX_SIZE);
        } catch (BlobStore.TooLarge e) {
            throw tooLarge();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (size == 0) {
            deleteQuietly(key);
            throw ApiException.rule("attachment.empty", "The file is empty");
        }
        var name = Attachment.safeFilename(filename);
        var type = Attachment.safeContentType(contentType);
        try {
            var id = attachments.insert(viewer, uploader, name, type, size, key, clock.instant());
            return new Attachment(id, name, type, size, key);
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Download download(Viewer viewer, UUID id) {
        var attachment = attachments.find(viewer, id).orElseThrow(ApiException::notFound);
        try {
            return new Download(attachment, blobs.open(attachment.storageKey()));
        } catch (IOException e) {
            log.error("Attachment {} has no content", id, e);
            throw ApiException.notFound();
        }
    }

    /** 閲覧者がアップロードした未紐付けの添付を、コメントに紐付ける。1 つでも紐付けられなければ 422（トランザクションごと戻す）。 */
    void attach(Viewer viewer, List<UUID> ids, UUID commentId) {
        var distinct = ids.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return;
        }
        if (distinct.size() > Attachment.MAX_PER_COMMENT) {
            throw ApiException.rule("attachment.too_many", "Too many attachments");
        }
        if (attachments.attach(viewer, uploaderOf(viewer), distinct, commentId) != distinct.size()) {
            throw ApiException.rule("attachment.not_found", "Attachment not found or already used");
        }
    }

    /** コメントに紐付かないまま 24 時間たった添付を消す（architecture.md 2.6）。消した件数を返す。 */
    public int deleteUnattached() {
        var before = clock.instant().minus(UNATTACHED_TTL);
        int deleted = 0;
        for (var tenantId : tenants.allIds()) {
            // 行を先に消す。中身の削除に失敗しても、参照されないファイルが残るだけ
            var keys = attachments.deleteUnattached(new Viewer.System(tenantId), before);
            keys.forEach(this::deleteQuietly);
            deleted += keys.size();
        }
        return deleted;
    }

    private void deleteQuietly(String key) {
        try {
            blobs.delete(key);
        } catch (IOException e) {
            log.warn("Could not delete attachment content {}", key, e);
        }
    }

    static ApiException tooLarge() {
        return new ApiException(HttpStatus.CONTENT_TOO_LARGE, "attachment.too_large", "Files must be 20 MB or smaller");
    }

    private static UserId uploaderOf(Viewer viewer) {
        return switch (viewer) {
            case Viewer.Staff s -> s.userId();
            case Viewer.Requester r -> r.userId();
            case Viewer.System _ -> throw new IllegalArgumentException("The system does not upload files");
        };
    }
}
