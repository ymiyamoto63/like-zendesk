package archfixture.view.infra;

import archfixture.ticket.infra.TicketRepository;

/** 違反例: 他の機能の infra に依存している。 */
public class ViewRepository {
    TicketRepository tickets;
}
