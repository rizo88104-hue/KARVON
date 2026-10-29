package uz.karvon.order.domain;

import uz.karvon.user.domain.Role;

import java.util.EnumSet;
import java.util.Set;

/**
 * Who changes an order status. SYSTEM is the application itself (payments, timers, rules).
 */
public enum Actor {
    SYSTEM, CUSTOMER, BUYER, OPERATOR, ADMIN;

    /** The staff actors a user with these roles can act as (CUSTOMER is not a staff actor). */
    public static Set<Actor> staffOf(Set<Role> roles) {
        Set<Actor> actors = EnumSet.noneOf(Actor.class);
        if (roles.contains(Role.ADMIN)) {
            actors.add(ADMIN);
        }
        if (roles.contains(Role.OPERATOR)) {
            actors.add(OPERATOR);
        }
        if (roles.contains(Role.BUYER)) {
            actors.add(BUYER);
        }
        return actors;
    }
}
