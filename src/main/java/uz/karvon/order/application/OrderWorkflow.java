package uz.karvon.order.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import uz.karvon.order.domain.Actor;
import uz.karvon.order.domain.Order;
import uz.karvon.order.domain.OrderStateMachine;
import uz.karvon.order.domain.OrderStatus;
import uz.karvon.order.domain.OrderStatusHistoryEntry;
import uz.karvon.order.infrastructure.OrderStatusHistoryRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * The only place that changes an order status. Asks {@link OrderStateMachine}, then applies what goes with the
 * new status (paid amount and dates, refund on cancel), writes history and publishes OrderStatusChangedEvent.
 * Callers run it inside their transaction.
 */
@Component
@RequiredArgsConstructor
public class OrderWorkflow {

    private final OrderStatusHistoryRepository history;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Writes the first history entry of a new (saved) order. */
    public void created(Order order, UUID customerId) {
        Instant now = Instant.now(clock);
        history.save(new OrderStatusHistoryEntry(order.getId(), null, order.getStatus(), customerId, Actor.CUSTOMER,
                null, now));
        events.publishEvent(new OrderStatusChangedEvent(order.getId(), order.getNumber(), order.getCustomerId(), null,
                order.getStatus(), Actor.CUSTOMER, now));
    }

    /** Checks that one of {@code actors} may make this change; returns that actor. Changes nothing. */
    public Actor check(Order order, OrderStatus to, Set<Actor> actors) {
        return OrderStateMachine.authorize(order.getStatus(), to, actors);
    }

    /**
     * Changes the status. {@code userId} is null for the system.
     * PAID records the full payment; CANCELLED requests a refund of what was paid and not yet returned.
     */
    public void change(Order order, OrderStatus to, Set<Actor> actors, UUID userId, String comment) {
        Actor actor = check(order, to, actors);
        Instant now = Instant.now(clock);
        OrderStatus from = order.getStatus();
        switch (to) {
            case PAID -> {
                order.setPaidUzs(order.getTotalUzs());
                order.setPaidAt(now);
            }
            case DELIVERED -> order.setDeliveredAt(now);
            case CANCELLED -> refund(order, order.refundableUzs(), RefundReason.ORDER_CANCELLED);
            default -> {
            }
        }
        order.setStatus(to);
        history.save(new OrderStatusHistoryEntry(order.getId(), from, to, actor == Actor.SYSTEM ? null : userId, actor,
                blankToNull(comment), now));
        events.publishEvent(new OrderStatusChangedEvent(order.getId(), order.getNumber(), order.getCustomerId(), from, to,
                actor, now));
    }

    /** Requests a refund (wallet, Task 7) and counts it on the order. Zero or less does nothing. */
    public void refund(Order order, BigDecimal amount, RefundReason reason) {
        if (amount.signum() <= 0) {
            return;
        }
        order.setRefundedUzs(order.getRefundedUzs().add(amount));
        events.publishEvent(new OrderRefundRequestedEvent(order.getId(), order.getNumber(), order.getCustomerId(), amount,
                reason));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
