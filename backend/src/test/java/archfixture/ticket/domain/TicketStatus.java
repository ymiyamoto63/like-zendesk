package archfixture.ticket.domain;

import archfixture.ticket.infra.TicketRepository;
import org.springframework.stereotype.Component;

/** 違反例: domain から infra と Spring に依存している。 */
@Component
public class TicketStatus {
    TicketRepository repository;
}
