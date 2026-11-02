package uz.karvon.notification.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRetryTest {

    private static final RetryBackoff BACKOFF = new RetryBackoff(Duration.ofMinutes(1), Duration.ofHours(1));
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Test
    void delayDoublesAfterEachFailureUpToTheMaximum() {
        assertThat(BACKOFF.delayAfter(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(BACKOFF.delayAfter(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(BACKOFF.delayAfter(5)).isEqualTo(Duration.ofMinutes(16));
        assertThat(BACKOFF.delayAfter(7)).isEqualTo(Duration.ofHours(1));
        assertThat(BACKOFF.delayAfter(1000)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void failedKeepsTheErrorAndWaitsForTheBackoff() {
        Notification notification = new Notification(UUID.randomUUID(), null, NotificationChannel.SMS, "order.paid",
                "[\"KRV-000001\"]", NOW);

        notification.failed("x".repeat(600), NOW, BACKOFF);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getLastError()).hasSize(500);
        // attempts is counted when the notification is taken for sending; 0 here counts as the first attempt
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
    }

    @Test
    void adminRetryMakesItPendingAndDueNow() {
        Notification notification = new Notification(UUID.randomUUID(), null, NotificationChannel.TELEGRAM,
                "order.paid", "[]", NOW);
        notification.failed("Telegram answered 403", NOW, BACKOFF);

        Instant later = NOW.plusSeconds(5);
        notification.retryNow(later);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getNextAttemptAt()).isEqualTo(later);

        notification.sent(later);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isEqualTo(later);
        assertThat(notification.getLastError()).isNull();
    }
}
