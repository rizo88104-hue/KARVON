package uz.karvon.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * A banner. {@code activeFrom}/{@code activeTo} (ISO-8601, optional) limit when it is shown; {@code activeTo} is exclusive.
 */
public record BannerRequest(
        @Schema(example = "11.11 chegirmalari") @NotBlank @Size(max = 150) String title,
        @Schema(example = "Xizmat haqi 50% arzon") @Size(max = 300) String subtitle,
        @Schema(example = "https://cdn.karvon.uz/banners/1111.jpg") @NotBlank @Size(max = 1000) String imageUrl,
        @Schema(example = "/catalog/search?q=krossovka") @Size(max = 1000) String linkUrl,
        @Min(0) int sortOrder,
        boolean active,
        Instant activeFrom,
        Instant activeTo) {
}
