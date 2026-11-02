package uz.karvon.notification.application;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import uz.karvon.order.application.OrderRefundRequestedEvent;
import uz.karvon.order.application.OrderStatusChangedEvent;
import uz.karvon.payment.application.PaymentCompletedEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Notifies customers about order events. Runs only after the business transaction committed (a rolled back change
 * sends nothing) and on the bounded "notificationExecutor", so a slow SMS or Telegram API never slows an order.
 * The outbox rows are saved first; whatever fails to send is retried by {@link NotificationRetryJob}.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    public static final String EXECUTOR = "notificationExecutor";

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notifications;
    private final NotificationDispatcher dispatcher;

    @Async(EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        deliver(event, () -> notifications.enqueue(event));
    }

    @Async(EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        deliver(event, () -> notifications.enqueue(event));
    }

    @Async(EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRefundRequested(OrderRefundRequestedEvent event) {
        deliver(event, () -> notifications.enqueue(event));
    }

    private void deliver(Object event, Supplier<List<UUID>> enqueue) {
        List<UUID> ids;
        try {
            ids = enqueue.get();
        } catch (RuntimeException ex) {
            log.error("Could not save notifications for {}", event, ex);
            return;
        }
        for (UUID id : ids) {
            try {
                dispatcher.send(id);
            } catch (RuntimeException ex) {
                // e.g. the database is down; the row stays PENDING and the retry job picks it up
                log.error("Could not send notification {}", id, ex);
            }
        }
    }
}
