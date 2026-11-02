package uz.karvon.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ShipRequest(
        @Schema(example = "CN123456789UZ") @NotBlank @Size(max = 100) String internationalTrackingNumber,
        @Schema(example = "Taobao Global") @Size(max = 100) String carrier,
        @Schema(example = "2026-11-20") LocalDate expectedArrivalDate,
        @Size(max = 500) String comment) {
}
