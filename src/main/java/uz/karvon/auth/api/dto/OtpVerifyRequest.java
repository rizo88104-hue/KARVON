package uz.karvon.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyRequest(
        @Schema(example = "+998901234567") @NotBlank String phone,
        @Schema(example = "12345") @NotBlank @Pattern(regexp = "\\d{5}") String code) {
}
