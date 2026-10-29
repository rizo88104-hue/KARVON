package uz.karvon.order.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import uz.karvon.common.application.BusinessException;
import uz.karvon.user.domain.Role;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    /** Every allowed transition and exactly who may do it (from the task, plus ADMIN for staff and system steps). */
    @ParameterizedTest(name = "{0} -> {1} by {2}")
    @CsvSource(delimiter = '|', textBlock = """
            AWAITING_PAYMENT          | PAID                      | SYSTEM ADMIN
            AWAITING_PAYMENT          | CANCELLED                 | CUSTOMER SYSTEM ADMIN
            PAID                      | PURCHASING                | BUYER ADMIN
            PAID                      | CANCELLED                 | CUSTOMER ADMIN
            PURCHASING                | PURCHASED                 | BUYER ADMIN
            PURCHASING                | OUT_OF_STOCK              | BUYER ADMIN
            PURCHASING                | PRICE_CHANGED             | BUYER ADMIN
            OUT_OF_STOCK              | PURCHASING                | CUSTOMER
            OUT_OF_STOCK              | CANCELLED                 | CUSTOMER
            PRICE_CHANGED             | PURCHASING                | CUSTOMER
            PRICE_CHANGED             | CANCELLED                 | CUSTOMER
            PURCHASED                 | AT_WAREHOUSE              | BUYER OPERATOR ADMIN
            AT_WAREHOUSE              | AWAITING_SHIPPING_PAYMENT | SYSTEM
            AT_WAREHOUSE              | IN_TRANSIT                | OPERATOR ADMIN
            AWAITING_SHIPPING_PAYMENT | IN_TRANSIT                | OPERATOR ADMIN
            IN_TRANSIT                | ARRIVED                   | OPERATOR ADMIN
            ARRIVED                   | DELIVERED                 | OPERATOR ADMIN
            """)
    void allowedTransitionsAndWhoMayDoThem(OrderStatus from, OrderStatus to, String actors) {
        Set<Actor> allowed = Arrays.stream(actors.split(" ")).map(Actor::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Actor.class)));

        assertThat(OrderStateMachine.allowedActors(from, to)).containsExactlyInAnyOrderElementsOf(allowed);
        for (Actor actor : Actor.values()) {
            if (allowed.contains(actor)) {
                assertThat(OrderStateMachine.authorize(from, to, Set.of(actor))).isEqualTo(actor);
            } else {
                assertThatThrownBy(() -> OrderStateMachine.authorize(from, to, Set.of(actor)))
                        .as(actor + " must not do " + from + " -> " + to)
                        .hasFieldOrPropertyWithValue("code", "STATUS_CHANGE_FORBIDDEN");
            }
        }
    }

    @Test
    void thereAreNoOtherTransitions() {
        int count = 0;
        for (OrderStatus from : OrderStatus.values()) {
            count += OrderStateMachine.next(from).size();
        }
        assertThat(count).isEqualTo(17);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "AWAITING_PAYMENT, PURCHASING",
            "AWAITING_PAYMENT, DELIVERED",
            "PAID, PURCHASED",
            "PAID, AWAITING_PAYMENT",
            "PURCHASING, CANCELLED",
            "PURCHASING, PAID",
            "PURCHASED, IN_TRANSIT",
            "PURCHASED, CANCELLED",
            "AT_WAREHOUSE, ARRIVED",
            "AWAITING_SHIPPING_PAYMENT, CANCELLED",
            "IN_TRANSIT, DELIVERED",
            "IN_TRANSIT, AT_WAREHOUSE",
            "ARRIVED, IN_TRANSIT",
            "OUT_OF_STOCK, PURCHASED",
            "PRICE_CHANGED, PAID",
            "DELIVERED, CANCELLED",
            "CANCELLED, PAID",
            "PAID, PAID"
    })
    void otherTransitionsAreInvalidForEveryone(OrderStatus from, OrderStatus to) {
        assertThatThrownBy(() -> OrderStateMachine.authorize(from, to, EnumSet.allOf(Actor.class)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_STATUS_TRANSITION");
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"DELIVERED", "CANCELLED"})
    void finalStatusesGoNowhere(OrderStatus status) {
        assertThat(OrderStateMachine.next(status)).isEmpty();
    }

    @Test
    void staffWithSeveralRolesActsAsTheFirstAllowedOne() {
        Set<Actor> buyerAndOperator = Actor.staffOf(Set.of(Role.CUSTOMER, Role.BUYER, Role.OPERATOR));

        assertThat(buyerAndOperator).containsExactlyInAnyOrder(Actor.BUYER, Actor.OPERATOR);
        assertThat(OrderStateMachine.authorize(OrderStatus.PAID, OrderStatus.PURCHASING, buyerAndOperator))
                .isEqualTo(Actor.BUYER);
        assertThat(OrderStateMachine.authorize(OrderStatus.IN_TRANSIT, OrderStatus.ARRIVED, buyerAndOperator))
                .isEqualTo(Actor.OPERATOR);
        assertThat(Actor.staffOf(Set.of(Role.CUSTOMER))).isEmpty();
    }

    @Test
    void customerDecisionCannotBeMadeByStaff() {
        assertThatThrownBy(() -> OrderStateMachine.authorize(OrderStatus.OUT_OF_STOCK, OrderStatus.PURCHASING,
                EnumSet.of(Actor.ADMIN, Actor.BUYER, Actor.OPERATOR)))
                .hasFieldOrPropertyWithValue("code", "STATUS_CHANGE_FORBIDDEN");
    }
}
