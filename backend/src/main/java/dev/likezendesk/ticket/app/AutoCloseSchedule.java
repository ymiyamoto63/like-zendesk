package dev.likezendesk.ticket.app;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 自動クローズの起動。結合テストでは app.scheduling.enabled=false にし、テストから直接 closeDue を呼ぶ。 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class AutoCloseSchedule {

    private final AutoClose autoClose;

    AutoCloseSchedule(AutoClose autoClose) {
        this.autoClose = autoClose;
    }

    @Scheduled(fixedDelayString = "${app.tickets.auto-close-delay:5m}")
    void run() {
        autoClose.closeDue();
    }
}
