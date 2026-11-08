package dev.likezendesk.notification.app;

import dev.likezendesk.notification.domain.NotificationKind;
import dev.likezendesk.notification.infra.OutboxRepository;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 通知を Outbox に書く。呼び出し側の業務データの更新と同じトランザクションで書くため、トランザクションを必須にする。 */
@Service
public class Notifications {

    private final OutboxRepository outbox;
    private final Clock clock;

    Notifications(OutboxRepository outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    /** {@code payload} はメールの宛先（{@code to}）と本文に入れる値。送信時ではなく、ここで決める。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Viewer viewer, NotificationKind kind, UserId recipient, Map<String, String> payload) {
        outbox.enqueue(viewer, kind, recipient, payload, clock.instant());
    }
}
