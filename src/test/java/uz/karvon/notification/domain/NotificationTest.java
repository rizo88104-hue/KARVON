package uz.karvon.notification.domain;

import org.junit.jupiter.api.Test;
import uz.karvon.user.domain.Language;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Test
    void failedNotificationIsRetriedFiveTimesWithGrowingDelays() {
        Notification notification = newNotification();
        List<Duration> delays = new ArrayList<>();

        // the first try and 5 retries
        for (int attempt = 1; attempt <= 6; attempt++) {
            notification.markFailed("Telegram answered 502", NOW);
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(notification.getAttempts()).isEqualTo(attempt);
            if (notification.willRetry()) {
                delays.add(Duration.between(NOW, notification.getNextAttemptAt()));
            }
        }

        assertThat(delays).containsExactly(Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(4),
                Duration.ofMinutes(8), Duration.ofMinutes(16));
        assertThat(notification.willRetry()).isFalse();
        assertThat(notification.getNextAttemptAt()).isNull();
        assertThat(notification.getLastError()).isEqualTo("Telegram answered 502");
    }

    @Test
    void sentAfterAFailureClearsTheError() {
        Notification notification = newNotification();
        notification.markFailed("x".repeat(800), NOW);
        assertThat(notification.getLastError()).hasSize(500);

        notification.markSent(NOW.plusSeconds(60));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getAttempts()).isEqualTo(2);
        assertThat(notification.getSentAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(notification.getLastError()).isNull();
        assertThat(notification.willRetry()).isFalse();
    }

    private static Notification newNotification() {
        Notification notification = new Notification(UUID.randomUUID(), null, NotificationChannel.TELEGRAM, "42",
                Language.UZ, "order.paid", "[\"K-1\"]");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        return notification;
    }
}
