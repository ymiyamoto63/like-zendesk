package dev.likezendesk.ticket.app;

import dev.likezendesk.shared.Viewer;
import dev.likezendesk.tenant.app.Tenants;
import dev.likezendesk.ticket.domain.Actor;
import dev.likezendesk.ticket.domain.Ticket;
import dev.likezendesk.ticket.domain.TicketStatus;
import dev.likezendesk.ticket.infra.TicketEventRepository;
import dev.likezendesk.ticket.infra.TicketRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 解決から 4 日たったチケットをクローズする（要件 7、docs/design/architecture.md 2.6）。監査ログの実行者はシステム。
 *
 * <p>テナントごとに Viewer.System を作り、1 回に {@link #BATCH_SIZE} 件ずつ、それぞれのトランザクションで処理する。
 */
@Service
public class AutoClose {

    public static final Duration AFTER = Duration.ofDays(4);
    static final int BATCH_SIZE = 100;

    private final Tenants tenants;
    private final TicketRepository tickets;
    private final TicketEventRepository events;
    private final TransactionTemplate transaction;
    private final Clock clock;

    AutoClose(
            Tenants tenants,
            TicketRepository tickets,
            TicketEventRepository events,
            TransactionTemplate transaction,
            Clock clock) {
        this.tenants = tenants;
        this.tickets = tickets;
        this.events = events;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** 期限の来たチケットをクローズし、クローズした件数を返す。 */
    public int closeDue() {
        int closed = 0;
        for (var tenantId : tenants.allIds()) {
            var viewer = new Viewer.System(tenantId);
            int batch;
            do {
                Integer count = transaction.execute(status -> closeBatch(viewer));
                batch = count == null ? 0 : count;
                closed += batch;
            } while (batch == BATCH_SIZE);
        }
        return closed;
    }

    private int closeBatch(Viewer.System viewer) {
        var now = clock.instant();
        var due = tickets.lockSolvedBefore(viewer, now.minus(AFTER), BATCH_SIZE);
        for (var ticket : due) {
            var applied = ticket.apply(Ticket.Update.statusOnly(TicketStatus.CLOSED), new Actor.System(), now);
            if (!tickets.update(viewer, applied.ticket(), ticket.version())) {
                throw new IllegalStateException("Ticket " + ticket.number() + " changed while locked");
            }
            events.insert(viewer, ticket.id(), UUID.randomUUID(), null, applied.changes(), now);
        }
        return due.size();
    }
}
