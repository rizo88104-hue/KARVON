package uz.karvon.notification.application;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sends again the FAILED notifications whose backoff time has come (at most 5 retries, see Notification),
 * and PENDING ones left over, e.g. by a restart between saving and sending.
 */
@Component
@RequiredArgsConstructor
public class NotificationRetryJob {

    /** A PENDING row older than this was not picked up by the event listener. */
    static final Duration PENDING_STUCK_AFTER = Duration.ofMinutes(2);
    private static final int BATCH = 100;

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryJob.class);

    private final NotificationService notifications;
    private final NotificationDispatcher dispatcher;
    private final Clock clock;

    /** Returns how many notifications were tried. */
    @Scheduled(fixedDelayString = "${karvon.notification.retry-check-ms:60000}",
            initialDelayString = "${karvon.notification.retry-check-ms:60000}")
    public int retryDue() {
        Instant now = Instant.now(clock);
        List<UUID> due = notifications.due(now, now.minus(PENDING_STUCK_AFTER), BATCH);
        for (UUID id : due) {
            try {
                dispatcher.send(id);
            } catch (RuntimeException ex) {
                log.warn("Could not retry notification {}", id, ex);
            }
        }
        if (!due.isEmpty()) {
            log.info("Retried {} notifications", due.size());
        }
        return due.size();
    }
}
