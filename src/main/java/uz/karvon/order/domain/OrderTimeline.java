package uz.karvon.order.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The customer's progress bar: the normal path of statuses, each DONE, CURRENT or TODO, with the time it was
 * (last) reached and the comment written then. AWAITING_SHIPPING_PAYMENT is shown only if the order went there.
 * An exception status (OUT_OF_STOCK, PRICE_CHANGED) is shown after PURCHASING while it is current;
 * a cancelled order shows the steps it reached and then CANCELLED.
 */
public final class OrderTimeline {

    public enum State {
        DONE, CURRENT, TODO
    }

    public record Step(OrderStatus status, State state, Instant at, String comment) {
    }

    static final List<OrderStatus> PATH = List.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID,
            OrderStatus.PURCHASING, OrderStatus.PURCHASED, OrderStatus.AT_WAREHOUSE,
            OrderStatus.AWAITING_SHIPPING_PAYMENT, OrderStatus.IN_TRANSIT, OrderStatus.ARRIVED, OrderStatus.DELIVERED);

    private OrderTimeline() {
    }

    /** {@code history} oldest first. */
    public static List<Step> build(OrderStatus current, List<OrderStatusHistoryEntry> history) {
        Map<OrderStatus, OrderStatusHistoryEntry> lastEntered = new HashMap<>();
        for (OrderStatusHistoryEntry entry : history) {
            lastEntered.put(entry.getToStatus(), entry);
        }
        List<OrderStatus> path = new ArrayList<>(PATH);
        if (current != OrderStatus.AWAITING_SHIPPING_PAYMENT && !lastEntered.containsKey(OrderStatus.AWAITING_SHIPPING_PAYMENT)) {
            path.remove(OrderStatus.AWAITING_SHIPPING_PAYMENT);
        }

        List<Step> steps = new ArrayList<>();
        if (current == OrderStatus.CANCELLED) {
            for (OrderStatus status : path) {
                if (lastEntered.containsKey(status)) {
                    steps.add(step(status, State.DONE, lastEntered));
                }
            }
            steps.add(step(OrderStatus.CANCELLED, State.CURRENT, lastEntered));
            return steps;
        }
        boolean exception = current == OrderStatus.OUT_OF_STOCK || current == OrderStatus.PRICE_CHANGED;
        int currentIndex = exception ? path.indexOf(OrderStatus.PURCHASING) : path.indexOf(current);
        for (int i = 0; i < path.size(); i++) {
            OrderStatus status = path.get(i);
            if (i < currentIndex || (exception && i == currentIndex) || (current == OrderStatus.DELIVERED && i == currentIndex)) {
                steps.add(step(status, State.DONE, lastEntered));
            } else if (i == currentIndex) {
                steps.add(step(status, State.CURRENT, lastEntered));
            } else {
                steps.add(new Step(status, State.TODO, null, null));
            }
            if (exception && i == currentIndex) {
                steps.add(step(current, State.CURRENT, lastEntered));
            }
        }
        return steps;
    }

    private static Step step(OrderStatus status, State state, Map<OrderStatus, OrderStatusHistoryEntry> lastEntered) {
        OrderStatusHistoryEntry entry = lastEntered.get(status);
        return new Step(status, state, entry == null ? null : entry.getCreatedAt(), entry == null ? null : entry.getComment());
    }
}
