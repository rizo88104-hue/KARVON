package uz.karvon.notification.application;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import uz.karvon.notification.domain.NotificationMessage;
import uz.karvon.order.application.OrderStatusChangedEvent;
import uz.karvon.order.application.PriceDifferenceDueEvent;
import uz.karvon.order.application.ShippingDifferenceDueEvent;
import uz.karvon.payment.application.PaymentCompletedEvent;

import java.util.Optional;

/**
 * Turns business events into notifications, only after their transaction committed: a rolled back change
 * notifies nobody. The outbox rows are written here; sending happens on the notification pool.
 * Errors are logged and never reach the business caller, whose transaction is already committed.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService outbox;
    private final NotificationDispatcher dispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        enqueue(NotificationRules.forStatus(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShippingDifference(ShippingDifferenceDueEvent event) {
        enqueue(Optional.of(NotificationRules.forShippingDifference(event)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPriceDifference(PriceDifferenceDueEvent event) {
        enqueue(Optional.of(NotificationRules.forPriceDifference(event)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        enqueue(NotificationRules.forPayment(event));
    }

    /** The rows are written: send them now (in the background). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueued(NotificationsQueuedEvent event) {
        try {
            dispatcher.dispatch(event.notificationIds());
        } catch (TaskRejectedException ex) {
            log.warn("Notification pool is full; {} notifications stay PENDING for the retry job",
                    event.notificationIds().size());
        }
    }

    private void enqueue(Optional<NotificationMessage> message) {
        if (message.isEmpty()) {
            return;
        }
        try {
            outbox.enqueue(message.get());
        } catch (RuntimeException ex) {
            log.error("Could not write notification {} for user {}", message.get().templateKey(),
                    message.get().userId(), ex);
        }
    }
}
