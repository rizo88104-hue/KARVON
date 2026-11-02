package uz.karvon.notification.application;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.karvon.user.application.UserContact;
import uz.karvon.user.application.UserService;

import java.util.Optional;
import java.util.UUID;

/**
 * Sends one notification: takes it from the outbox, renders the text in the user's language, sends it over its
 * channel and records SENT or FAILED. No database transaction is open while the provider is called.
 */
@Component
@RequiredArgsConstructor
public class NotificationDelivery {

    private static final Logger log = LoggerFactory.getLogger(NotificationDelivery.class);

    private final NotificationService outbox;
    private final UserService users;
    private final TemplateRenderer renderer;
    private final SmsSender sms;
    private final TelegramBotClient telegram;

    /** Returns whether it was sent now. */
    public boolean deliver(UUID notificationId) {
        Optional<NotificationService.Outgoing> claimed = outbox.claim(notificationId);
        if (claimed.isEmpty()) {
            return false;
        }
        NotificationService.Outgoing outgoing = claimed.get();
        try {
            UserContact contact = users.contact(outgoing.userId())
                    .orElseThrow(() -> new IllegalStateException("User not found"));
            String text = renderer.render(outgoing.templateKey(), outgoing.params(), contact.language());
            switch (outgoing.channel()) {
                case SMS -> sms.send(contact.phone(), text);
                case TELEGRAM -> {
                    if (!contact.telegramLinked()) {
                        throw new IllegalStateException("Telegram is not linked");
                    }
                    telegram.sendMessage(contact.telegramChatId(), text);
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Notification {} ({} {}) was not sent: {}", notificationId, outgoing.channel(),
                    outgoing.templateKey(), ex.getMessage());
            outbox.markFailed(notificationId, error(ex));
            return false;
        }
        outbox.markSent(notificationId);
        return true;
    }

    private static String error(RuntimeException ex) {
        Throwable cause = ex.getCause() != null && ex.getCause().getMessage() != null ? ex.getCause() : ex;
        return ex.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
