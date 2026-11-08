package dev.likezendesk.ticket.web;

import dev.likezendesk.ticket.domain.Attachment;
import java.util.List;
import java.util.UUID;

/** 添付の情報。中身は {@code GET /api/t/{slug}/attachments/{id}} で取る。保存キーは返さない。 */
record AttachmentResponse(UUID id, String filename, String contentType, long size) {

    static AttachmentResponse of(Attachment a) {
        return new AttachmentResponse(a.id(), a.filename(), a.contentType(), a.size());
    }

    static List<AttachmentResponse> of(List<Attachment> all) {
        return all.stream().map(AttachmentResponse::of).toList();
    }
}
