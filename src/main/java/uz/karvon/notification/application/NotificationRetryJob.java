package uz.karvon.notification.application;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Sends again what failed (with a growing delay, up to karvon.notifications.max-retries times) and what stayed
 * PENDING because the notification pool was full or the application stopped.
 */
@Component
@RequiredArgsConstructor
public class NotificationRetryJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryJob.class);

    private final NotificationService outbox;
    private final NotificationDelivery delivery;

    /** Returns how many were sent. */
    @Scheduled(fixedDelayString = "${karvon.notifications.retry-check-ms:60000}",
            initialDelayString = "${karvon.notifications.retry-check-ms:60000}")
    public int retryDue() {
        int sent = 0;
        for (UUID id : outbox.due()) {
            try {
                if (delivery.deliver(id)) {
                    sent++;
                }
            } catch (RuntimeException ex) {
                log.warn("Could not retry notification {}", id, ex);
            }
        }
        if (sent > 0) {
            log.info("Retried and sent {} notifications", sent);
        }
        return sent;
    }
}
