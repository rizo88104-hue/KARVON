package uz.karvon.notification.application;

import uz.karvon.common.domain.Money;
import uz.karvon.notification.domain.NotificationMessage;
import uz.karvon.order.application.OrderRefundRequestedEvent;
import uz.karvon.order.application.OrderStatusChangedEvent;
import uz.karvon.order.application.PaymentPurpose;
import uz.karvon.order.application.PriceDifferenceDueEvent;
import uz.karvon.order.application.RefundReason;
import uz.karvon.order.application.ShippingDifferenceDueEvent;
import uz.karvon.order.domain.OrderStatus;
import uz.karvon.payment.application.PaymentCompletedEvent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Which event becomes which message (template key in messages_uz / messages_ru and its parameters).
 * Important messages (paid, shipping or price difference to pay, price changed, out of stock, arrived, delivered,
 * cancelled, out-of-stock refund) also go by SMS when Telegram is linked.
 * <p>
 * AWAITING_SHIPPING_PAYMENT has no status message: {@link ShippingDifferenceDueEvent} brings the amount.
 * A paid order is told by the PAID status (it also covers payments confirmed by an admin), so
 * {@link PaymentCompletedEvent} only tells about the extra payments (shipping and price differences).
 */
public final class NotificationRules {

    private NotificationRules() {
    }

    /**
     * The message for a status change. {@code reviewLink} gives the "leave a rating" link of an order number
     * (DELIVERED). PURCHASING says nothing: PURCHASED tells the customer.
     */
    public static Optional<NotificationMessage> forStatus(OrderStatusChangedEvent event, UnaryOperator<String> reviewLink) {
        if (event.from() == null || event.to() == null) {
            return Optional.empty();
        }
        String number = event.number();
        OrderStatusChangedEvent.Details details = event.details() == null
                ? OrderStatusChangedEvent.Details.NONE : event.details();
        return switch (event.to()) {
            case DELIVERED -> message(event, "order.delivered", true, number, reviewLink.apply(number));
            case CANCELLED -> cancelled(event, details.refundedUzs());
            case PAID -> message(event, "order.paid", true, number);
            case PURCHASED -> message(event, "order.purchased", false, number);
            case AT_WAREHOUSE -> message(event, "order.at_warehouse", false, number, weight(details.realWeightKg()));
            case IN_TRANSIT -> message(event, "order.in_transit", false, number,
                    orDash(details.internationalTrackingNumber()));
            case ARRIVED -> message(event, "order.arrived", true, number, orEmpty(details.pickupInfo()));
            case OUT_OF_STOCK -> message(event, "order.out_of_stock", true, number);
            case PRICE_CHANGED -> message(event, "order.price_changed", true, number);
            default -> Optional.empty();
        };
    }

    /**
     * Refunds told by themselves: only the out-of-stock items of an order that goes on. A cancel refund is told by
     * the CANCELLED message, a cheaper shipping is not told.
     */
    public static Optional<NotificationMessage> forRefund(OrderRefundRequestedEvent event) {
        if (event.reason() != RefundReason.ITEMS_OUT_OF_STOCK || event.amountUzs().signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new NotificationMessage(event.customerId(), event.orderId(), "order.out_of_stock_refund",
                List.of(event.number(), Money.formatUzsNumber(event.amountUzs())), true));
    }

    /**
     * With the refunded amount when money went back to the wallet. After OUT_OF_STOCK (the customer chose the
     * refund) the reason is the missing goods.
     */
    private static Optional<NotificationMessage> cancelled(OrderStatusChangedEvent event, BigDecimal refunded) {
        if (refunded == null || refunded.signum() <= 0) {
            return message(event, "order.cancelled", true, event.number());
        }
        String key = event.from() == OrderStatus.OUT_OF_STOCK ? "order.out_of_stock_refund" : "order.cancelled_refunded";
        return message(event, key, true, event.number(), Money.formatUzsNumber(refunded));
    }

    public static NotificationMessage forShippingDifference(ShippingDifferenceDueEvent event) {
        return new NotificationMessage(event.customerId(), event.orderId(), "order.shipping_difference",
                List.of(event.number(), Money.formatUzsNumber(event.amountUzs())), true);
    }

    public static NotificationMessage forPriceDifference(PriceDifferenceDueEvent event) {
        return new NotificationMessage(event.customerId(), event.orderId(), "order.price_difference",
                List.of(event.number(), Money.formatUzsNumber(event.amountUzs())), true);
    }

    public static Optional<NotificationMessage> forPayment(PaymentCompletedEvent event) {
        if (event.purpose() == PaymentPurpose.ORDER) {
            return Optional.empty();
        }
        return Optional.of(new NotificationMessage(event.customerId(), event.orderId(), "payment.received",
                List.of(event.orderNumber(), Money.formatUzsNumber(event.amountUzs())), false));
    }

    private static Optional<NotificationMessage> message(OrderStatusChangedEvent event, String key, boolean important,
                                                         String... params) {
        return Optional.of(new NotificationMessage(event.customerId(), event.orderId(), key, List.of(params), important));
    }

    private static String weight(BigDecimal kg) {
        return kg == null ? "-" : kg.stripTrailingZeros().toPlainString();
    }

    private static String orDash(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }

    private static String orEmpty(String text) {
        return text == null ? "" : text;
    }
}
