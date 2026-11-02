package uz.karvon.notification.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uz.karvon.notification.domain.NotificationMessage;
import uz.karvon.order.application.OrderNotificationFacts;
import uz.karvon.order.application.OrderRefundRequestedEvent;
import uz.karvon.order.application.OrderStatusChangedEvent;
import uz.karvon.order.application.PaymentPurpose;
import uz.karvon.order.application.RefundReason;
import uz.karvon.order.domain.Actor;
import uz.karvon.order.domain.OrderStatus;
import uz.karvon.payment.application.PaymentCompletedEvent;
import uz.karvon.payment.domain.PaymentProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class NotificationPlannerTest {

    private static final String NUMBER = "K-261102-0001";
    private static final OrderNotificationFacts FACTS = new OrderNotificationFacts(NUMBER, new BigDecimal("1.250"),
            new BigDecimal("32000"), "CN123UZ", "Chilonzor 9, kargo ofisi");

    private final NotificationPlanner planner = new NotificationPlanner();

    @Test
    void importantStatusesWithTheirValues() {
        assertThat(status(OrderStatus.PAID)).contains(message("order.paid", true, NUMBER));
        assertThat(status(OrderStatus.AWAITING_SHIPPING_PAYMENT))
                .contains(message("order.shipping_difference", true, NUMBER, "32 000"));
        assertThat(status(OrderStatus.ARRIVED)).contains(message("order.arrived", true, NUMBER, "Chilonzor 9, kargo ofisi"));
        assertThat(status(OrderStatus.OUT_OF_STOCK)).contains(message("order.out_of_stock", true, NUMBER));
        assertThat(status(OrderStatus.PRICE_CHANGED)).contains(message("order.price_changed", true, NUMBER));
    }

    @Test
    void otherStatusesAreNotImportant() {
        assertThat(status(OrderStatus.PURCHASED)).contains(message("order.purchased", false, NUMBER));
        assertThat(status(OrderStatus.AT_WAREHOUSE)).contains(message("order.at_warehouse", false, NUMBER, "1.25"));
        assertThat(status(OrderStatus.IN_TRANSIT)).contains(message("order.in_transit", false, NUMBER, "CN123UZ"));
        assertThat(status(OrderStatus.DELIVERED)).contains(message("order.delivered", false, NUMBER));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"AWAITING_PAYMENT", "PURCHASING"})
    void statusesWithoutANotification(OrderStatus to) {
        assertThat(planner.forStatusChange(event(OrderStatus.PAID, to, Actor.SYSTEM), noFacts())).isEmpty();
    }

    @Test
    void missingDetailsAreShownAsADash() {
        OrderNotificationFacts unknown = OrderNotificationFacts.unknown(NUMBER);
        assertThat(planner.forStatusChange(event(OrderStatus.PURCHASED, OrderStatus.IN_TRANSIT, Actor.OPERATOR), () -> unknown))
                .contains(message("order.in_transit", false, NUMBER, "-"));
        assertThat(planner.forStatusChange(event(OrderStatus.IN_TRANSIT, OrderStatus.ARRIVED, Actor.OPERATOR), () -> unknown))
                .contains(message("order.arrived", true, NUMBER, ""));
    }

    @Test
    void factsAreNotReadWhenTheMessageDoesNotNeedThem() {
        assertThat(planner.forStatusChange(event(OrderStatus.PURCHASING, OrderStatus.PURCHASED, Actor.SYSTEM), noFacts()))
                .isPresent();
    }

    @Test
    void cancelledIsAnnouncedOnlyForPaidOrdersCancelledByStaffOrSystem() {
        assertThat(planner.forStatusChange(event(OrderStatus.PAID, OrderStatus.CANCELLED, Actor.ADMIN), noFacts()))
                .contains(message("order.cancelled", false, NUMBER));
        // unpaid order cancelled after 30 minutes
        assertThat(planner.forStatusChange(event(OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCELLED, Actor.SYSTEM), noFacts()))
                .isEmpty();
        // the customer chose a refund themselves
        assertThat(planner.forStatusChange(event(OrderStatus.OUT_OF_STOCK, OrderStatus.CANCELLED, Actor.CUSTOMER), noFacts()))
                .isEmpty();
    }

    @Test
    void paymentsAnnounceOnlyExtraPayments() {
        assertThat(planner.forPayment(payment(PaymentPurpose.ORDER, "238200"))).isEmpty();
        assertThat(planner.forPayment(payment(PaymentPurpose.SHIPPING_DIFFERENCE, "32000")))
                .contains(message("order.difference_paid", false, NUMBER, "32 000"));
        assertThat(planner.forPayment(payment(PaymentPurpose.PRICE_DIFFERENCE, "1234550")))
                .contains(message("order.difference_paid", false, NUMBER, "1 234 600"));
    }

    @Test
    void shippingRefundIsAnnounced() {
        UUID id = UUID.randomUUID();
        assertThat(planner.forRefund(new OrderRefundRequestedEvent(id, NUMBER, id, new BigDecimal("16000"),
                RefundReason.SHIPPING_OVERPAID))).contains(message("order.shipping_refund", false, NUMBER, "16 000"));
        assertThat(planner.forRefund(new OrderRefundRequestedEvent(id, NUMBER, id, new BigDecimal("16000"),
                RefundReason.ORDER_CANCELLED))).isEmpty();
    }

    private Optional<NotificationMessage> status(OrderStatus to) {
        return planner.forStatusChange(event(OrderStatus.PURCHASED, to, Actor.OPERATOR), () -> FACTS);
    }

    private static OrderStatusChangedEvent event(OrderStatus from, OrderStatus to, Actor actor) {
        return new OrderStatusChangedEvent(UUID.randomUUID(), NUMBER, UUID.randomUUID(), from, to, actor, Instant.now());
    }

    private static PaymentCompletedEvent payment(PaymentPurpose purpose, String amount) {
        return new PaymentCompletedEvent(UUID.randomUUID(), UUID.randomUUID(), NUMBER, UUID.randomUUID(), purpose,
                PaymentProvider.PAYME, new BigDecimal(amount), Instant.now());
    }

    private static Supplier<OrderNotificationFacts> noFacts() {
        return () -> fail("order details should not be read");
    }

    private static NotificationMessage message(String key, boolean important, String... params) {
        return new NotificationMessage(key, List.of(params), important);
    }
}
