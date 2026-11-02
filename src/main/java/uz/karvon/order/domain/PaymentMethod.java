package uz.karvon.order.domain;

public enum PaymentMethod {
    PAYME, CLICK,
    /** Paid from the customer wallet when the order is created. */
    WALLET
}
