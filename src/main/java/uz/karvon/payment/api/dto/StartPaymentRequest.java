package uz.karvon.payment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import uz.karvon.payment.domain.PaymentProvider;

public record StartPaymentRequest(@Schema(example = "PAYME") @NotNull PaymentProvider provider) {
}
