package uz.karvon.settings.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

/**
 * The full new set of limits: order status name -> maximum hours. Statuses left out have no limit.
 */
public record UpdateSlaSettingsRequest(
        @Schema(example = "{\"PAID\": 48, \"PURCHASED\": 240, \"IN_TRANSIT\": 600}")
        @NotEmpty Map<@Pattern(regexp = "[A-Z_]{2,30}") String, @NotNull @Min(1) @Max(100_000) Integer> limits) {
}
