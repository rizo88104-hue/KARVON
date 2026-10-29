package uz.karvon.catalog.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.karvon.catalog.domain.BlockedCategory;
import uz.karvon.translation.domain.Lang;

/**
 * ZH keywords block any title that contains them; UZ/RU keywords block titles with that whole word.
 */
public record BlockedKeywordRequest(
        @Schema(example = "香水") @NotBlank @Size(max = 100) String keyword,
        @Schema(example = "ZH") @NotNull Lang lang,
        @Schema(example = "LIQUID") @NotNull BlockedCategory category) {
}
