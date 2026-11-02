package uz.karvon.order.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import uz.karvon.order.domain.Actor;
import uz.karvon.order.domain.Order;
import uz.karvon.order.domain.OrderItemStatus;
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
                order.getStatus(), Actor.CUSTOMER, now, OrderStatusChangedEvent.Details.NONE));
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
                actor, now, new OrderStatusChangedEvent.Details(order.getRealWeightKg(),
                order.getInternationalTrackingNumber(), order.getPickupInfo())));
    }

    /**
     * While PURCHASING: once every item is purchased or out of stock (and at least one was purchased), the order
     * becomes PURCHASED. Done by the system after the last item or the customer decision.
     */
    public void finishPurchasingIfDone(Order order) {
        boolean allResolved = order.getItems().stream().allMatch(item -> item.getStatus().resolved());
        boolean anyPurchased = order.getItems().stream().anyMatch(item -> item.getStatus() == OrderItemStatus.PURCHASED);
        if (order.getStatus() == OrderStatus.PURCHASING && allResolved && anyPurchased) {
            change(order, OrderStatus.PURCHASED, Set.of(Actor.SYSTEM), null, null);
        }
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

    /** The weighed parcel costs more to ship than prepaid; tells the customer (notifications). */
    public void shippingDifferenceDue(Order order) {
        events.publishEvent(new ShippingDifferenceDueEvent(order.getId(), order.getNumber(), order.getCustomerId(),
                order.shippingDifferenceDueUzs()));
    }

    /** The customer accepted a higher price; {@code amount} more is due. Zero or less does nothing. */
    public void priceDifferenceDue(Order order, BigDecimal amount) {
        if (amount.signum() > 0) {
            events.publishEvent(new PriceDifferenceDueEvent(order.getId(), order.getNumber(), order.getCustomerId(),
                    amount));
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
