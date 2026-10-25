package archfixture.ticket.app;

import archfixture.ticket.infra.TicketRepository;

/** 許される例: app から同じ機能の infra に依存している。 */
public class TicketService {
    TicketRepository repository;
}
