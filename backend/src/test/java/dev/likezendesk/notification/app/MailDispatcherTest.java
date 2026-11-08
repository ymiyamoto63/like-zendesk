package dev.likezendesk.notification.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MailDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");

    @Test
    void retriesWithExponentialBackoffAndGivesUpAfterFiveAttempts() {
        assertThat(MailDispatcher.retryAt(NOW, 1)).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        assertThat(MailDispatcher.retryAt(NOW, 2)).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
        assertThat(MailDispatcher.retryAt(NOW, 3)).isEqualTo(NOW.plus(Duration.ofMinutes(4)));
        assertThat(MailDispatcher.retryAt(NOW, 4)).isEqualTo(NOW.plus(Duration.ofMinutes(8)));
        assertThat(MailDispatcher.retryAt(NOW, 5)).isNull();
    }
}
