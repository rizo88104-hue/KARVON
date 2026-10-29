package uz.karvon.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrderCommentRequest(@NotBlank @Size(max = 2000) String text) {
}
