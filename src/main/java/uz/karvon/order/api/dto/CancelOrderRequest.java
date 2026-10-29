package uz.karvon.order.api.dto;

import jakarta.validation.constraints.Size;

public record CancelOrderRequest(@Size(max = 500) String reason) {
}
