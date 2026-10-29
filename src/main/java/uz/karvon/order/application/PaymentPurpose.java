package uz.karvon.order.application;

/**
 * What a payment is for: the order itself, or the extra shipping found when the parcel was weighed.
 */
public enum PaymentPurpose {
    ORDER, SHIPPING_DIFFERENCE
}
