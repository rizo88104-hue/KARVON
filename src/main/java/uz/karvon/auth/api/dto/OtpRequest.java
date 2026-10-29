package uz.karvon.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record OtpRequest(@Schema(example = "+998901234567") @NotBlank String phone) {
}
