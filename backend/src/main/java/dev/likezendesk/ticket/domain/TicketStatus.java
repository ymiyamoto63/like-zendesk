package dev.likezendesk.ticket.domain;

import java.util.Locale;

/** ステータス（要件 7）。 */
public enum TicketStatus {
    NEW,
    OPEN,
    PENDING,
    ON_HOLD,
    SOLVED,
    CLOSED;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TicketStatus fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
