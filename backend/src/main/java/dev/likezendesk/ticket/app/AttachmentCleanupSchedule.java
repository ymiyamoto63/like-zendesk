package dev.likezendesk.ticket.app;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 不要な添付の削除（architecture.md 2.6）。結合テストでは app.scheduling.enabled=false にし、テストから直接呼ぶ。 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class AttachmentCleanupSchedule {

    private final Attachments attachments;

    AttachmentCleanupSchedule(Attachments attachments) {
        this.attachments = attachments;
    }

    @Scheduled(fixedDelayString = "${app.attachments.cleanup-delay:1h}")
    void run() {
        attachments.deleteUnattached();
    }
}
