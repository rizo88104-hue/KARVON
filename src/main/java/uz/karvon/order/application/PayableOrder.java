package uz.karvon.order.application;

import uz.karvon.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What the payment module needs to know about an order: whether it can be paid for this purpose now and how much.
 *
 * @param amountDueUzs the exact amount to pay; zero unless {@code state} is PAYABLE
 */
public record PayableOrder(UUID orderId, String number, UUID customerId, OrderStatus status, PaymentPurpose purpose,
                           State state, BigDecimal amountDueUzs) {

    public enum State {
        /** Can be paid now. */
        PAYABLE,
        /** Paid already for this purpose. */
        ALREADY_PAID,
        /** The order was cancelled. */
        CANCELLED,
        /** Nothing to pay for this purpose yet (e.g. the parcel is not weighed). */
        NOT_DUE
    }

    public boolean payable() {
        return state == State.PAYABLE;
    }
}
