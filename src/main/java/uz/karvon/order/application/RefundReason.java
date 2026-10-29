package uz.karvon.order.application;

public enum RefundReason {
    /** The order was cancelled after payment. */
    ORDER_CANCELLED,
    /** The customer accepted an order with some items out of stock; those items are refunded. */
    ITEMS_OUT_OF_STOCK,
    /** The real shipping was cheaper than prepaid. */
    SHIPPING_OVERPAID
}
