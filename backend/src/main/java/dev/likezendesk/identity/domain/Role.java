package dev.likezendesk.identity.domain;

import java.util.Locale;

public enum Role {
    REQUESTER,
    AGENT,
    ADMIN;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Role fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
