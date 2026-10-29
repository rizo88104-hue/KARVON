package uz.karvon.wallet.domain;

/**
 * Why the wallet balance changed. Money in: REFUND, SHIPPING_REFUND, positive ADMIN_ADJUSTMENT.
 * Money out: ORDER_PAYMENT, SHIPPING_PAYMENT, CARD_REFUND, negative ADMIN_ADJUSTMENT.
 */
public enum WalletTransactionType {
    /** An order was cancelled or items were out of stock. */
    REFUND,
    /** The real shipping was cheaper than prepaid. */
    SHIPPING_REFUND,
    /** An order paid from the wallet. */
    ORDER_PAYMENT,
    /** A shipping difference paid from the wallet. */
    SHIPPING_PAYMENT,
    /** A correction by ADMIN, with a reason. */
    ADMIN_ADJUSTMENT,
    /** ADMIN sent money from the wallet back to the customer card by hand. */
    CARD_REFUND
}
