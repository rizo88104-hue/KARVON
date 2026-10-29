package uz.karvon.order.domain;

import org.springframework.http.HttpStatus;
import uz.karvon.common.application.BusinessException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static uz.karvon.order.domain.Actor.ADMIN;
import static uz.karvon.order.domain.Actor.BUYER;
import static uz.karvon.order.domain.Actor.CUSTOMER;
import static uz.karvon.order.domain.Actor.OPERATOR;
import static uz.karvon.order.domain.Actor.SYSTEM;
import static uz.karvon.order.domain.OrderStatus.ARRIVED;
import static uz.karvon.order.domain.OrderStatus.AT_WAREHOUSE;
import static uz.karvon.order.domain.OrderStatus.AWAITING_PAYMENT;
import static uz.karvon.order.domain.OrderStatus.AWAITING_SHIPPING_PAYMENT;
import static uz.karvon.order.domain.OrderStatus.CANCELLED;
import static uz.karvon.order.domain.OrderStatus.DELIVERED;
import static uz.karvon.order.domain.OrderStatus.IN_TRANSIT;
import static uz.karvon.order.domain.OrderStatus.OUT_OF_STOCK;
import static uz.karvon.order.domain.OrderStatus.PAID;
import static uz.karvon.order.domain.OrderStatus.PRICE_CHANGED;
import static uz.karvon.order.domain.OrderStatus.PURCHASED;
import static uz.karvon.order.domain.OrderStatus.PURCHASING;

/**
 * Every allowed order status change and who may make it. Nothing else may change an order's status.
 * ADMIN may also do what BUYER, OPERATOR and SYSTEM do (manual handling, e.g. confirming a payment),
 * but not the customer's own decisions. Extra conditions (weight entered, difference paid) are checked
 * by the order service.
 */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Map<OrderStatus, Set<Actor>>> RULES = new EnumMap<>(OrderStatus.class);

    static {
        allow(AWAITING_PAYMENT, PAID, SYSTEM, ADMIN);
        allow(AWAITING_PAYMENT, CANCELLED, CUSTOMER, SYSTEM, ADMIN);
        allow(PAID, PURCHASING, BUYER, ADMIN);
        // The customer may cancel a paid order until purchasing starts; the money is refunded.
        allow(PAID, CANCELLED, CUSTOMER, ADMIN);
        allow(PURCHASING, PURCHASED, BUYER, ADMIN);
        allow(PURCHASING, OUT_OF_STOCK, BUYER, ADMIN);
        allow(PURCHASING, PRICE_CHANGED, BUYER, ADMIN);
        // The customer's decision: ACCEPT goes back to purchasing, REFUND cancels with a refund.
        allow(OUT_OF_STOCK, PURCHASING, CUSTOMER);
        allow(OUT_OF_STOCK, CANCELLED, CUSTOMER);
        allow(PRICE_CHANGED, PURCHASING, CUSTOMER);
        allow(PRICE_CHANGED, CANCELLED, CUSTOMER);
        allow(PURCHASED, AT_WAREHOUSE, BUYER, OPERATOR, ADMIN);
        // Set automatically when the real shipping is higher than prepaid.
        allow(AT_WAREHOUSE, AWAITING_SHIPPING_PAYMENT, SYSTEM);
        allow(AT_WAREHOUSE, IN_TRANSIT, OPERATOR, ADMIN);
        allow(AWAITING_SHIPPING_PAYMENT, IN_TRANSIT, OPERATOR, ADMIN);
        allow(IN_TRANSIT, ARRIVED, OPERATOR, ADMIN);
        allow(ARRIVED, DELIVERED, OPERATOR, ADMIN);
    }

    private OrderStateMachine() {
    }

    /**
     * Checks a change and returns the actor it is done as (the first of {@code callerActors} that is allowed).
     * Throws INVALID_STATUS_TRANSITION (400) if the change does not exist,
     * STATUS_CHANGE_FORBIDDEN (403) if none of the caller's actors may make it.
     */
    public static Actor authorize(OrderStatus from, OrderStatus to, Set<Actor> callerActors) {
        Set<Actor> allowed = allowedActors(from, to);
        if (allowed.isEmpty()) {
            throw new BusinessException("INVALID_STATUS_TRANSITION", "error.order.invalidTransition", from, to);
        }
        for (Actor actor : Actor.values()) {
            if (callerActors.contains(actor) && allowed.contains(actor)) {
                return actor;
            }
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "STATUS_CHANGE_FORBIDDEN", "error.order.transitionForbidden",
                from, to);
    }

    /** Who may change {@code from} to {@code to}; empty if the change is not allowed at all. */
    public static Set<Actor> allowedActors(OrderStatus from, OrderStatus to) {
        Set<Actor> actors = RULES.getOrDefault(from, Map.of()).get(to);
        return actors == null ? Set.of() : Collections.unmodifiableSet(actors);
    }

    /** Statuses reachable from {@code from} by anyone. */
    public static Set<OrderStatus> next(OrderStatus from) {
        Map<OrderStatus, Set<Actor>> targets = RULES.get(from);
        return targets == null ? Set.of() : Collections.unmodifiableSet(targets.keySet());
    }

    private static void allow(OrderStatus from, OrderStatus to, Actor... actors) {
        RULES.computeIfAbsent(from, status -> new EnumMap<>(OrderStatus.class))
                .put(to, EnumSet.copyOf(Set.of(actors)));
    }
}
