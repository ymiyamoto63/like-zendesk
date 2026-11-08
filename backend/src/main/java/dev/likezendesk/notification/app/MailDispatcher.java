package dev.likezendesk.notification.app;

import dev.likezendesk.notification.domain.MailTemplates;
import dev.likezendesk.notification.infra.OutboxRepository;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.tenant.app.Tenants;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox の未送信の通知をメールで送る。失敗したら指数バックオフで再試行し、5 回失敗したらあきらめる。
 *
 * <p>テナントごとに Viewer.System を作り、1 テナント分を 1 トランザクションで処理する。行ロック（SKIP LOCKED）により、複数のプロセスで動かしても同じ通知を二重に送らない。
 */
@Service
public class MailDispatcher {

    static final int BATCH_SIZE = 50;
    static final int MAX_ATTEMPTS = 5;

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    private final Tenants tenants;
    private final OutboxRepository outbox;
    private final MailSender mailSender;
    private final TransactionTemplate transaction;
    private final Clock clock;

    MailDispatcher(
            Tenants tenants,
            OutboxRepository outbox,
            MailSender mailSender,
            TransactionTemplate transaction,
            Clock clock) {
        this.tenants = tenants;
        this.outbox = outbox;
        this.mailSender = mailSender;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** 送信時刻の来た通知を送り、送った件数を返す。 */
    public int dispatchDue() {
        int sent = 0;
        for (var tenantId : tenants.allIds()) {
            var viewer = new Viewer.System(tenantId);
            Integer count = transaction.execute(status -> dispatch(viewer));
            sent += count == null ? 0 : count;
        }
        return sent;
    }

    private int dispatch(Viewer.System viewer) {
        int sent = 0;
        var now = clock.instant();
        for (var pending : outbox.lockDue(viewer, now, BATCH_SIZE)) {
            var mail = MailTemplates.render(pending.kind(), pending.payload());
            var message = new SimpleMailMessage();
            message.setTo(mail.to());
            message.setSubject(mail.subject());
            message.setText(mail.body());
            try {
                mailSender.send(message);
                outbox.markSent(viewer, pending.id(), now);
                sent++;
            } catch (MailException e) {
                log.warn("Failed to send notification {} (attempt {})", pending.id(), pending.attempts() + 1, e);
                outbox.markFailed(viewer, pending.id(), retryAt(now, pending.attempts() + 1));
            }
        }
        return sent;
    }

    /** {@code attempts} 回目の失敗の後に再試行する時刻。1, 2, 4, 8 分後。上限に達したら null。 */
    static @Nullable Instant retryAt(Instant now, int attempts) {
        if (attempts >= MAX_ATTEMPTS) {
            return null;
        }
        return now.plus(Duration.ofMinutes(1L << (attempts - 1)));
    }
}
