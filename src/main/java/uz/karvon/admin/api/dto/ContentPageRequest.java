package uz.karvon.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A text page; the bodies are Markdown. {@code slug}: lower-case Latin letters, digits and dashes, e.g. "faq".
 */
public record ContentPageRequest(
        @Schema(example = "faq") @NotBlank @Size(max = 60) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") String slug,
        @Schema(example = "Ko'p so'raladigan savollar") @NotBlank @Size(max = 200) String titleUz,
        @Schema(example = "Частые вопросы") @NotBlank @Size(max = 200) String titleRu,
        @Schema(example = "## Qanday buyurtma beraman?\n\nTaobao havolasini qo'ying...") @NotNull @Size(max = 100_000) String bodyUz,
        @Schema(example = "## Как оформить заказ?\n\nВставьте ссылку Taobao...") @NotNull @Size(max = 100_000) String bodyRu,
        boolean published) {
}
