package dev.likezendesk.notification.app;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** メール送信ジョブの起動。結合テストでは app.scheduling.enabled=false にし、テストから直接 dispatchDue を呼ぶ。 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class MailDispatchSchedule {

    private final MailDispatcher dispatcher;

    MailDispatchSchedule(MailDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${app.mail.dispatch-delay:10s}")
    void run() {
        dispatcher.dispatchDue();
    }
}
