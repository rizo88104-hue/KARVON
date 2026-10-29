package uz.karvon.order.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The customer answer when the buyer found an item out of stock or at a new price.
 * ACCEPT: continue (out-of-stock items are refunded); REFUND: cancel the order and get the money back.
 */
public record DecisionRequest(@NotNull Action action) {

    public enum Action {
        ACCEPT, REFUND
    }
}
