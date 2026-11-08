package dev.likezendesk.identity.domain;

import java.time.Duration;
import java.util.Locale;

/** メールで送るトークンの用途と有効期間。 */
public enum TokenPurpose {
    INVITATION(Duration.ofDays(7)),
    EMAIL_VERIFICATION(Duration.ofHours(24)),
    PASSWORD_RESET(Duration.ofHours(1));

    private final Duration validity;

    TokenPurpose(Duration validity) {
        this.validity = validity;
    }

    public Duration validity() {
        return validity;
    }

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
