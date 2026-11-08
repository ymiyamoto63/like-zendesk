package dev.likezendesk.devtools.app;

import dev.likezendesk.devtools.domain.AdjustableClock;
import dev.likezendesk.notification.app.MailDispatcher;
import dev.likezendesk.ticket.app.Attachments;
import dev.likezendesk.ticket.app.AutoClose;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** E2E 用に時刻を進め、定期ジョブをすぐに実行する（受け入れ基準 8、api.md 2.6）。 */
@Service
@Profile("e2e")
public class TimeTravel {

    private final AdjustableClock clock;
    private final AutoClose autoClose;
    private final MailDispatcher mails;
    private final Attachments attachments;

    TimeTravel(AdjustableClock clock, AutoClose autoClose, MailDispatcher mails, Attachments attachments) {
        this.clock = clock;
        this.autoClose = autoClose;
        this.mails = mails;
        this.attachments = attachments;
    }

    public record Result(Instant now, int closed, int mailed) {}

    public Result advance(Duration amount) {
        if (amount.isNegative()) {
            throw new IllegalArgumentException("The clock only moves forward");
        }
        clock.advance(amount);
        var closed = autoClose.closeDue();
        attachments.deleteUnattached();
        return new Result(clock.instant(), closed, mails.dispatchDue());
    }
}
