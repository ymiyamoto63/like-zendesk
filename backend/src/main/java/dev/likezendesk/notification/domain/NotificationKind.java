package dev.likezendesk.notification.domain;

import java.util.Locale;

public enum NotificationKind {
    INVITATION,
    EMAIL_VERIFICATION,
    PASSWORD_RESET,
    TICKET_CREATED,
    PUBLIC_COMMENT,
    ASSIGNED,
    SOLVED;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static NotificationKind fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
