package uz.karvon.order.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.order.domain.OrderStatus.ARRIVED;
import static uz.karvon.order.domain.OrderStatus.AT_WAREHOUSE;
import static uz.karvon.order.domain.OrderStatus.AWAITING_PAYMENT;
import static uz.karvon.order.domain.OrderStatus.AWAITING_SHIPPING_PAYMENT;
import static uz.karvon.order.domain.OrderStatus.CANCELLED;
import static uz.karvon.order.domain.OrderStatus.DELIVERED;
import static uz.karvon.order.domain.OrderStatus.IN_TRANSIT;
import static uz.karvon.order.domain.OrderStatus.OUT_OF_STOCK;
import static uz.karvon.order.domain.OrderStatus.PAID;
import static uz.karvon.order.domain.OrderStatus.PURCHASED;
import static uz.karvon.order.domain.OrderStatus.PURCHASING;
import static uz.karvon.order.domain.OrderTimeline.State.CURRENT;
import static uz.karvon.order.domain.OrderTimeline.State.DONE;
import static uz.karvon.order.domain.OrderTimeline.State.TODO;

class OrderTimelineTest {

    private static final Instant T0 = Instant.parse("2026-10-29T10:00:00Z");

    @Test
    void newOrderWaitsForPaymentAndSkipsTheShippingPaymentStep() {
        List<OrderTimeline.Step> steps = OrderTimeline.build(AWAITING_PAYMENT, history(AWAITING_PAYMENT));

        assertThat(steps).extracting(OrderTimeline.Step::status)
                .containsExactly(AWAITING_PAYMENT, PAID, PURCHASING, PURCHASED, AT_WAREHOUSE, IN_TRANSIT, ARRIVED, DELIVERED);
        assertThat(steps).extracting(OrderTimeline.Step::state)
                .containsExactly(CURRENT, TODO, TODO, TODO, TODO, TODO, TODO, TODO);
        assertThat(steps.getFirst().at()).isEqualTo(T0);
        assertThat(steps.get(1).at()).isNull();
    }

    @Test
    void shippingPaymentStepAppearsOnceReachedAndDeliveredIsAllDone() {
        List<OrderTimeline.Step> steps = OrderTimeline.build(DELIVERED, history(AWAITING_PAYMENT, PAID, PURCHASING,
                PURCHASED, AT_WAREHOUSE, AWAITING_SHIPPING_PAYMENT, IN_TRANSIT, ARRIVED, DELIVERED));

        assertThat(steps).hasSize(9).allMatch(step -> step.state() == DONE && step.at() != null);
        assertThat(steps.get(5).status()).isEqualTo(AWAITING_SHIPPING_PAYMENT);
    }

    @Test
    void exceptionIsShownAfterPurchasing() {
        List<OrderTimeline.Step> steps = OrderTimeline.build(OUT_OF_STOCK,
                history(AWAITING_PAYMENT, PAID, PURCHASING, OUT_OF_STOCK));

        assertThat(steps).extracting(OrderTimeline.Step::status)
                .containsSequence(PURCHASING, OUT_OF_STOCK, PURCHASED);
        assertThat(steps).extracting(OrderTimeline.Step::state)
                .containsExactly(DONE, DONE, DONE, CURRENT, TODO, TODO, TODO, TODO, TODO);
    }

    @Test
    void cancelledShowsWhatWasReachedThenCancelled() {
        List<OrderTimeline.Step> steps = OrderTimeline.build(CANCELLED, history(AWAITING_PAYMENT, PAID, CANCELLED));

        assertThat(steps).extracting(OrderTimeline.Step::status).containsExactly(AWAITING_PAYMENT, PAID, CANCELLED);
        assertThat(steps).extracting(OrderTimeline.Step::state).containsExactly(DONE, DONE, CURRENT);
    }

    /** One entry per status, a minute apart. */
    private static List<OrderStatusHistoryEntry> history(OrderStatus... statuses) {
        List<OrderStatusHistoryEntry> entries = new ArrayList<>();
        OrderStatus previous = null;
        for (int i = 0; i < statuses.length; i++) {
            entries.add(new OrderStatusHistoryEntry(UUID.randomUUID(), previous, statuses[i], null, Actor.SYSTEM, null,
                    T0.plusSeconds(60L * i)));
            previous = statuses[i];
        }
        return entries;
    }
}
