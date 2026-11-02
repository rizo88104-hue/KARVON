package uz.karvon.notification.application;

import org.junit.jupiter.api.Test;
import uz.karvon.notification.domain.NotificationMessage;
import uz.karvon.order.application.OrderStatusChangedEvent;
import uz.karvon.order.application.PaymentPurpose;
import uz.karvon.order.application.PriceDifferenceDueEvent;
import uz.karvon.order.application.ShippingDifferenceDueEvent;
import uz.karvon.order.domain.Actor;
import uz.karvon.order.domain.OrderStatus;
import uz.karvon.payment.application.PaymentCompletedEvent;
import uz.karvon.payment.domain.PaymentProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRulesTest {

    private static final UUID ORDER = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final String NUMBER = "KRV-000042";

    @Test
    void statusMessagesAndTheirParameters() {
        assertMessage(status(OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID, null), "order.paid", true, NUMBER);
        assertMessage(status(OrderStatus.PURCHASING, OrderStatus.PURCHASED, null), "order.purchased", false, NUMBER);
        assertMessage(status(OrderStatus.PURCHASED, OrderStatus.AT_WAREHOUSE,
                        new OrderStatusChangedEvent.Details(new BigDecimal("1.200"), null, null)),
                "order.at_warehouse", false, NUMBER, "1.2");
        assertMessage(status(OrderStatus.AT_WAREHOUSE, OrderStatus.IN_TRANSIT,
                        new OrderStatusChangedEvent.Details(new BigDecimal("1.2"), "CN123UZ", null)),
                "order.in_transit", false, NUMBER, "CN123UZ");
        assertMessage(status(OrderStatus.IN_TRANSIT, OrderStatus.ARRIVED,
                        new OrderStatusChangedEvent.Details(null, "CN123UZ", "Chilonzor 9, 10:00-19:00")),
                "order.arrived", true, NUMBER, "Chilonzor 9, 10:00-19:00");
        assertMessage(status(OrderStatus.PURCHASING, OrderStatus.OUT_OF_STOCK, null), "order.out_of_stock", true, NUMBER);
        assertMessage(status(OrderStatus.PURCHASING, OrderStatus.PRICE_CHANGED, null), "order.price_changed", true, NUMBER);
    }

    @Test
    void missingParcelDataIsShownAsDashOrNothing() {
        assertMessage(status(OrderStatus.AT_WAREHOUSE, OrderStatus.IN_TRANSIT, OrderStatusChangedEvent.Details.NONE),
                "order.in_transit", false, NUMBER, "-");
        assertMessage(status(OrderStatus.IN_TRANSIT, OrderStatus.ARRIVED, null), "order.arrived", true, NUMBER, "");
    }

    @Test
    void otherStatusesSayNothing() {
        assertThat(NotificationRules.forStatus(status(null, OrderStatus.AWAITING_PAYMENT, null))).isEmpty();
        assertThat(NotificationRules.forStatus(status(OrderStatus.PAID, OrderStatus.PURCHASING, null))).isEmpty();
        assertThat(NotificationRules.forStatus(status(OrderStatus.ARRIVED, OrderStatus.DELIVERED, null))).isEmpty();
        assertThat(NotificationRules.forStatus(status(OrderStatus.PAID, OrderStatus.CANCELLED, null))).isEmpty();
        // the amount comes with ShippingDifferenceDueEvent
        assertThat(NotificationRules.forStatus(status(OrderStatus.AT_WAREHOUSE, OrderStatus.AWAITING_SHIPPING_PAYMENT,
                null))).isEmpty();
    }

    @Test
    void differencesToPayAreImportantAndCarryTheAmount() {
        NotificationMessage shipping = NotificationRules.forShippingDifference(
                new ShippingDifferenceDueEvent(ORDER, NUMBER, CUSTOMER, new BigDecimal("32000")));
        assertThat(shipping.templateKey()).isEqualTo("order.shipping_difference");
        assertThat(shipping.params()).containsExactly(NUMBER, "32 000");
        assertThat(shipping.important()).isTrue();

        NotificationMessage price = NotificationRules.forPriceDifference(
                new PriceDifferenceDueEvent(ORDER, NUMBER, CUSTOMER, new BigDecimal("1234500")));
        assertThat(price.templateKey()).isEqualTo("order.price_difference");
        assertThat(price.params()).containsExactly(NUMBER, "1 234 500");
        assertThat(price.important()).isTrue();
    }

    @Test
    void orderPaymentIsToldByThePaidStatusExtraPaymentsByThemselves() {
        assertThat(NotificationRules.forPayment(payment(PaymentPurpose.ORDER))).isEmpty();

        Optional<NotificationMessage> shipping = NotificationRules.forPayment(payment(PaymentPurpose.SHIPPING_DIFFERENCE));
        assertThat(shipping).hasValueSatisfying(message -> {
            assertThat(message.templateKey()).isEqualTo("payment.received");
            assertThat(message.params()).containsExactly(NUMBER, "32 000");
            assertThat(message.important()).isFalse();
            assertThat(message.userId()).isEqualTo(CUSTOMER);
            assertThat(message.orderId()).isEqualTo(ORDER);
        });
    }

    private static void assertMessage(OrderStatusChangedEvent event, String key, boolean important, String... params) {
        assertThat(NotificationRules.forStatus(event)).hasValueSatisfying(message -> {
            assertThat(message.templateKey()).isEqualTo(key);
            assertThat(message.params()).containsExactly(params);
            assertThat(message.important()).isEqualTo(important);
            assertThat(message.userId()).isEqualTo(CUSTOMER);
            assertThat(message.orderId()).isEqualTo(ORDER);
        });
    }

    private static OrderStatusChangedEvent status(OrderStatus from, OrderStatus to,
                                                  OrderStatusChangedEvent.Details details) {
        return new OrderStatusChangedEvent(ORDER, NUMBER, CUSTOMER, from, to, Actor.SYSTEM, Instant.now(), details);
    }

    private static PaymentCompletedEvent payment(PaymentPurpose purpose) {
        return new PaymentCompletedEvent(UUID.randomUUID(), ORDER, NUMBER, CUSTOMER, purpose, PaymentProvider.PAYME,
                new BigDecimal("32000"), Instant.now());
    }
}
