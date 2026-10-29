package uz.karvon.cart.api.dto;

import jakarta.validation.constraints.NotNull;

public record SelectAllRequest(@NotNull Boolean selected) {
}
