package uz.karvon.notification.application;

import org.junit.jupiter.api.Test;
import uz.karvon.notification.domain.NotificationMessage;
import uz.karvon.notification.infrastructure.NotificationProperties;
import uz.karvon.order.application.OrderRefundRequestedEvent;
import uz.karvon.order.application.OrderStatusChangedEvent;
import uz.karvon.order.application.PaymentPurpose;
import uz.karvon.order.application.PriceDifferenceDueEvent;
import uz.karvon.order.application.RefundReason;
import uz.karvon.order.application.ShippingDifferenceDueEvent;
import uz.karvon.order.domain.Actor;
import uz.karvon.order.domain.OrderStatus;
import uz.karvon.payment.application.PaymentCompletedEvent;
import uz.karvon.payment.domain.PaymentProvider;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRulesTest {

    private static final UUID ORDER = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final String NUMBER = "KRV-000042";
    private static final UnaryOperator<String> LINK = properties("https://karvon.uz", "")::reviewLink;

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
    void deliveredThanksWithTheRatingLinkAlsoBySms() {
        assertMessage(status(OrderStatus.ARRIVED, OrderStatus.DELIVERED, null), "order.delivered", true, NUMBER,
                "https://karvon.uz/account/orders/" + NUMBER);
    }

    @Test
    void cancelledTellsTheRefundOnlyWhenMoneyWentBack() {
        assertMessage(status(OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCELLED, null), "order.cancelled", true, NUMBER);
        assertMessage(status(OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCELLED, refunded("0")),
                "order.cancelled", true, NUMBER);
        assertMessage(status(OrderStatus.PAID, OrderStatus.CANCELLED, refunded("238200")),
                "order.cancelled_refunded", true, NUMBER, "238 200");
        // The customer chose the refund for missing goods: the message names the reason.
        assertMessage(status(OrderStatus.OUT_OF_STOCK, OrderStatus.CANCELLED, refunded("238200")),
                "order.out_of_stock_refund", true, NUMBER, "238 200");
    }

    @Test
    void onlyOutOfStockItemRefundsAreToldByTheRefundEvent() {
        assertThat(NotificationRules.forRefund(refund("89000", RefundReason.ITEMS_OUT_OF_STOCK)))
                .hasValueSatisfying(message -> {
                    assertThat(message.templateKey()).isEqualTo("order.out_of_stock_refund");
                    assertThat(message.params()).containsExactly(NUMBER, "89 000");
                    assertThat(message.important()).isTrue();
                    assertThat(message.userId()).isEqualTo(CUSTOMER);
                    assertThat(message.orderId()).isEqualTo(ORDER);
                });
        // told by the CANCELLED message
        assertThat(NotificationRules.forRefund(refund("238200", RefundReason.ORDER_CANCELLED))).isEmpty();
        assertThat(NotificationRules.forRefund(refund("8000", RefundReason.SHIPPING_OVERPAID))).isEmpty();
        assertThat(NotificationRules.forRefund(refund("0", RefundReason.ITEMS_OUT_OF_STOCK))).isEmpty();
    }

    @Test
    void ratingLinkIsTheOrderPageOrTheConfiguredUrl() {
        assertThat(properties("https://karvon.uz/", "").reviewLink(NUMBER))
                .isEqualTo("https://karvon.uz/account/orders/" + NUMBER);
        assertThat(properties("https://karvon.uz", " https://g.page/r/emarket/review?o={number} ").reviewLink(NUMBER))
                .isEqualTo("https://g.page/r/emarket/review?o=" + NUMBER);
    }

    @Test
    void otherStatusesSayNothing() {
        assertThat(NotificationRules.forStatus(status(null, OrderStatus.AWAITING_PAYMENT, null), LINK)).isEmpty();
        // PURCHASED tells the customer
        assertThat(NotificationRules.forStatus(status(OrderStatus.PAID, OrderStatus.PURCHASING, null), LINK)).isEmpty();
        // the amount comes with ShippingDifferenceDueEvent
        assertThat(NotificationRules.forStatus(status(OrderStatus.AT_WAREHOUSE, OrderStatus.AWAITING_SHIPPING_PAYMENT,
                null), LINK)).isEmpty();
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
        assertThat(NotificationRules.forStatus(event, LINK)).hasValueSatisfying(message -> {
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

    private static OrderStatusChangedEvent.Details refunded(String amount) {
        return new OrderStatusChangedEvent.Details(null, null, null, new BigDecimal(amount));
    }

    private static OrderRefundRequestedEvent refund(String amount, RefundReason reason) {
        return new OrderRefundRequestedEvent(ORDER, NUMBER, CUSTOMER, new BigDecimal(amount), reason);
    }

    private static NotificationProperties properties(String siteUrl, String reviewUrl) {
        return new NotificationProperties(5, Duration.ofMinutes(1), Duration.ofHours(1), Duration.ofMinutes(5), 100, 2,
                1000, siteUrl, reviewUrl);
    }

    private static PaymentCompletedEvent payment(PaymentPurpose purpose) {
        return new PaymentCompletedEvent(UUID.randomUUID(), ORDER, NUMBER, CUSTOMER, purpose, PaymentProvider.PAYME,
                new BigDecimal("32000"), Instant.now());
    }
}
