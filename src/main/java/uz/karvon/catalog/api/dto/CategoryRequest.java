package uz.karvon.catalog.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A category. {@code parentId} null makes it a top-level category. {@code searchKeywordZh} is the Chinese search
 * text opened on Taobao (one or more words separated by spaces).
 */
public record CategoryRequest(
        UUID parentId,
        @Schema(example = "krossovkalar") @NotBlank @Size(max = 60) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") String slug,
        @Schema(example = "Krossovkalar") @NotBlank @Size(max = 100) String nameUz,
        @Schema(example = "Кроссовки") @NotBlank @Size(max = 100) String nameRu,
        @Schema(example = "运动鞋") @NotBlank @Size(max = 100) String searchKeywordZh,
        @Schema(example = "https://cdn.karvon.uz/icons/sneakers.svg") @Size(max = 300) String icon,
        @Min(0) int sortOrder) {
}
