package uz.karvon.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A manual correction: positive adds money, negative takes it (never below zero). The reason is kept in the ledger.
 */
public record AdjustWalletRequest(
        @Schema(example = "10000") @NotNull @Digits(integer = 14, fraction = 0) BigDecimal amount,
        @Schema(example = "Kompensatsiya: kechikkan yetkazish") @NotBlank @Size(max = 500) String reason) {
}
