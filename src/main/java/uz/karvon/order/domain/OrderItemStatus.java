package uz.karvon.order.domain;

/**
 * Item-level result of purchasing, set by the buyer together with the order status.
 */
public enum OrderItemStatus {
    OK, OUT_OF_STOCK, PRICE_CHANGED
}
