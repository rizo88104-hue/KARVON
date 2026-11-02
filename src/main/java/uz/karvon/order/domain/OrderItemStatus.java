package uz.karvon.order.domain;

/**
 * Item state while purchasing. PENDING: not bought yet. PRICE_CHANGED: the Taobao price rose above the threshold,
 * the customer decides. OUT_OF_STOCK: cannot be bought.
 */
public enum OrderItemStatus {
    PENDING, PURCHASED, OUT_OF_STOCK, PRICE_CHANGED;

    /** Nothing more to do for the item while purchasing. */
    public boolean resolved() {
        return this == PURCHASED || this == OUT_OF_STOCK;
    }
}
