package uz.karvon.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A ticker message. {@code linkUrl} is optional: a site path (e.g. /pages/faq) or a full https:// link.
 */
public record TickerMessageRequest(
        @Schema(example = "11.11 — xizmat haqi 50% arzon") @NotBlank @Size(max = 200) String textUz,
        @Schema(example = "11.11 — сервисный сбор на 50% меньше") @NotBlank @Size(max = 200) String textRu,
        @Schema(example = "/search?q=krossovka") @Size(max = 1000) String linkUrl,
        @Min(0) int sortOrder,
        boolean active) {
}
