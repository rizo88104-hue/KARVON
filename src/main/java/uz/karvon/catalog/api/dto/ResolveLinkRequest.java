package uz.karvon.catalog.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A link or the whole share text copied from the Taobao app.
 */
public record ResolveLinkRequest(
        @Schema(example = "https://item.taobao.com/item.htm?id=680000001117") @NotBlank @Size(max = 2000) String url) {
}
