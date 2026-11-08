package dev.likezendesk.ticket.domain;

import java.util.List;

/** 一覧の 1 ページと、条件に合う全件数。 */
public record TicketPage(List<Ticket> items, long total) {

    public TicketPage {
        items = List.copyOf(items);
    }
}
