package uz.karvon.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * ADMIN sent this amount from the wallet to the customer card outside the app (card number, date, reference).
 */
public record CardRefundRequest(
        @Schema(example = "238200") @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 0)
        BigDecimal amount,
        @Schema(example = "Payme kabinetidan qaytarildi, 8600 **** 1234") @NotBlank @Size(max = 500) String comment) {
}
