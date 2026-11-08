package dev.likezendesk.ticket.domain;

import java.util.Locale;

/** 種別（F-T-4）。 */
public enum TicketType {
    QUESTION,
    INCIDENT,
    PROBLEM,
    TASK;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TicketType fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
