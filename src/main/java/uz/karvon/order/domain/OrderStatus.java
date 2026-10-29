package uz.karvon.order.domain;

/**
 * Order statuses in their normal order; OUT_OF_STOCK, PRICE_CHANGED and CANCELLED are exceptions.
 * Allowed changes are in {@link OrderStateMachine}.
 */
public enum OrderStatus {
    AWAITING_PAYMENT, PAID, PURCHASING, PURCHASED, AT_WAREHOUSE, AWAITING_SHIPPING_PAYMENT, IN_TRANSIT, ARRIVED,
    DELIVERED, OUT_OF_STOCK, PRICE_CHANGED, CANCELLED
}
