package dev.likezendesk.ticket.domain;

import java.util.Locale;

/** 優先度（F-T-3）。 */
public enum Priority {
    LOW,
    NORMAL,
    HIGH,
    URGENT;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Priority fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
