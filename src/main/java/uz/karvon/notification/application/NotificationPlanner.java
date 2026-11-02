package uz.karvon.notification.application;

import org.springframework.stereotype.Component;
import uz.karvon.common.domain.Money;
import uz.karvon.notification.domain.NotificationMessage;
import uz.karvon.order.application.OrderNotificationFacts;
import uz.karvon.order.application.OrderRefundRequestedEvent;
import uz.karvon.order.application.OrderStatusChangedEvent;
import uz.karvon.order.application.PaymentPurpose;
import uz.karvon.order.domain.Actor;
import uz.karvon.order.domain.OrderStatus;
import uz.karvon.order.application.RefundReason;
import uz.karvon.payment.application.PaymentCompletedEvent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Decides what an event tells the customer (template key, values, importance), or that it tells nothing.
 * <ul>
 *   <li>Important (also by SMS when Telegram is linked): paid, shipping difference, price changed, out of stock, arrived.</li>
 *   <li>"Paid" comes from the PAID status, so an order marked paid by staff is announced too; a payment event
 *       announces only the extra payments (shipping or price difference).</li>
 *   <li>A cancelled order is announced only if it had been paid and the customer did not cancel it themselves.</li>
 * </ul>
 */
@Component
public class NotificationPlanner {

    /** {@code facts} is read only for the messages that need order details. */
    public Optional<NotificationMessage> forStatusChange(OrderStatusChangedEvent event,
                                                         Supplier<OrderNotificationFacts> facts) {
        String number = event.number();
        return switch (event.to()) {
            case PAID -> message("order.paid", true, number);
            case PURCHASED -> message("order.purchased", false, number);
            case AT_WAREHOUSE -> message("order.at_warehouse", false, number, weight(facts.get().realWeightKg()));
            case AWAITING_SHIPPING_PAYMENT -> message("order.shipping_difference", true, number,
                    amount(facts.get().shippingDifferenceUzs()));
            case IN_TRANSIT -> message("order.in_transit", false, number, orDash(facts.get().trackingNumber()));
            case ARRIVED -> message("order.arrived", true, number, orEmpty(facts.get().pickupInfo()));
            case OUT_OF_STOCK -> message("order.out_of_stock", true, number);
            case PRICE_CHANGED -> message("order.price_changed", true, number);
            case DELIVERED -> message("order.delivered", false, number);
            case CANCELLED -> wasPaid(event.from()) && event.actor() != Actor.CUSTOMER
                    ? message("order.cancelled", false, number)
                    : Optional.empty();
            default -> Optional.empty();
        };
    }

    public Optional<NotificationMessage> forPayment(PaymentCompletedEvent event) {
        if (event.purpose() == PaymentPurpose.ORDER) {
            return Optional.empty();
        }
        return message("order.difference_paid", false, event.orderNumber(), amount(event.amountUzs()));
    }

    public Optional<NotificationMessage> forRefund(OrderRefundRequestedEvent event) {
        if (event.reason() != RefundReason.SHIPPING_OVERPAID) {
            return Optional.empty();
        }
        return message("order.shipping_refund", false, event.number(), amount(event.amountUzs()));
    }

    private static boolean wasPaid(OrderStatus from) {
        return from != null && from != OrderStatus.AWAITING_PAYMENT;
    }

    private static Optional<NotificationMessage> message(String key, boolean important, String... params) {
        return Optional.of(new NotificationMessage(key, List.of(params), important));
    }

    private static String weight(BigDecimal kg) {
        return kg == null ? "-" : kg.stripTrailingZeros().toPlainString();
    }

    private static String amount(BigDecimal uzs) {
        return uzs == null ? "-" : Money.formatAmount(uzs.abs());
    }

    private static String orDash(String text) {
        return text == null || text.isBlank() ? "-" : text.trim();
    }

    private static String orEmpty(String text) {
        return text == null ? "" : text.trim();
    }
}
