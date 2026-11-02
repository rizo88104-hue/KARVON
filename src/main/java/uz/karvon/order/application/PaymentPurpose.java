package uz.karvon.order.application;

/**
 * What a payment is for: the order itself, the extra shipping found when the parcel was weighed, or the extra cost
 * of items that became more expensive on Taobao (accepted by the customer).
 */
public enum PaymentPurpose {
    ORDER, SHIPPING_DIFFERENCE, PRICE_DIFFERENCE
}
