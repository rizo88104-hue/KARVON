package uz.karvon.notification.application;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.notification.domain.Notification;
import uz.karvon.notification.domain.NotificationStatus;
import uz.karvon.notification.infrastructure.NotificationRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Sends one outbox row and records the result. The row is locked while sending (SKIP LOCKED), so the event
 * listener, the retry job and an admin retry never send the same message twice in parallel.
 */
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationRepository notifications;
    private final NotificationParams params;
    private final NotificationTemplates templates;
    private final SmsSender sms;
    private final TelegramSender telegram;
    private final Clock clock;

    /** Does nothing if the row is already sent, missing or being sent by someone else. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(UUID id) {
        Notification notification = notifications.lockForSending(id).orElse(null);
        if (notification == null || notification.getStatus() == NotificationStatus.SENT) {
            return;
        }
        Instant now = Instant.now(clock);
        try {
            String text = templates.render(notification.getTemplateKey(), params.read(notification.getParams()),
                    notification.getLanguage());
            switch (notification.getChannel()) {
                case SMS -> sms.send(notification.getRecipient(), text);
                case TELEGRAM -> telegram.send(Long.parseLong(notification.getRecipient()), text);
            }
            notification.markSent(now);
        } catch (RuntimeException ex) {
            notification.markFailed(describe(ex), now);
            log.warn("Notification {} ({} {}) failed, attempt {}{}: {}", id, notification.getChannel(),
                    notification.getTemplateKey(), notification.getAttempts(),
                    notification.willRetry() ? "" : ", no more automatic retries", notification.getLastError());
        }
    }

    private static String describe(RuntimeException ex) {
        StringBuilder text = new StringBuilder(ex.getClass().getSimpleName());
        if (ex.getMessage() != null) {
            text.append(": ").append(ex.getMessage());
        }
        if (ex.getCause() != null && ex.getCause() != ex) {
            text.append(" (").append(ex.getCause().getClass().getSimpleName());
            if (ex.getCause().getMessage() != null) {
                text.append(": ").append(ex.getCause().getMessage());
            }
            text.append(')');
        }
        return text.toString();
    }
}
