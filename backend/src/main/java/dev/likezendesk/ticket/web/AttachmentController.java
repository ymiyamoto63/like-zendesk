package dev.likezendesk.ticket.web;

import dev.likezendesk.shared.Viewer;
import dev.likezendesk.ticket.app.Attachments;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 添付のアップロードとダウンロード（F-T-7）。スタッフにも依頼者にも使え、見えるかどうかは親コメントの可視性で決まる（api.md 2.2、2.4）。
 */
@RestController
class AttachmentController {

    private final Attachments attachments;

    AttachmentController(Attachments attachments) {
        this.attachments = attachments;
    }

    /** 1 ファイル 20MB まで。返した ID を、コメントや起票の {@code attachmentIds} に入れる。 */
    @PostMapping(path = "/api/t/{slug}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    AttachmentResponse upload(Viewer viewer, @RequestPart("file") MultipartFile file) {
        try (var content = file.getInputStream()) {
            return AttachmentResponse.of(
                    attachments.upload(viewer, file.getOriginalFilename(), file.getContentType(), content));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** ブラウザで開かず、必ずダウンロードさせる（HTML として実行させないため）。 */
    @GetMapping("/api/t/{slug}/attachments/{id}")
    ResponseEntity<InputStreamResource> download(Viewer viewer, @PathVariable UUID id) {
        var download = attachments.download(viewer, id);
        var file = download.attachment();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.size())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new InputStreamResource(download.content()));
    }
}
