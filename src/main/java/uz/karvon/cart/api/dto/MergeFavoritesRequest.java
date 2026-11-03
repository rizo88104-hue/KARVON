package uz.karvon.cart.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Favorites a guest saved in the browser, newest first, to keep after logging in.
 */
public record MergeFavoritesRequest(
        @Schema(example = "[\"680000001117\", \"680000002234\"]")
        @NotNull @Size(max = 100) List<@NotBlank @Size(max = 20) String> externalIds) {
}
