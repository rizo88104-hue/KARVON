package uz.karvon.order.domain;

import uz.karvon.common.application.BusinessException;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Tabs of the customer order list and the statuses each shows. Cancelled orders appear only under ALL.
 */
public enum OrderTab {
    ALL(EnumSet.allOf(OrderStatus.class)),
    AWAITING_PAYMENT(EnumSet.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.AWAITING_SHIPPING_PAYMENT)),
    IN_PROGRESS(EnumSet.of(OrderStatus.PAID, OrderStatus.PURCHASING, OrderStatus.OUT_OF_STOCK, OrderStatus.PRICE_CHANGED,
            OrderStatus.PURCHASED, OrderStatus.AT_WAREHOUSE)),
    IN_TRANSIT(EnumSet.of(OrderStatus.IN_TRANSIT, OrderStatus.ARRIVED)),
    DELIVERED(EnumSet.of(OrderStatus.DELIVERED));

    private final Set<OrderStatus> statuses;

    OrderTab(Set<OrderStatus> statuses) {
        this.statuses = statuses;
    }

    public Set<OrderStatus> statuses() {
        return statuses;
    }

    /** Reads "all", "awaiting_payment", "in-progress" and so on, in any case. */
    public static OrderTab fromParam(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("INVALID_ORDER_TAB", "error.order.invalidTab");
        }
    }
}
