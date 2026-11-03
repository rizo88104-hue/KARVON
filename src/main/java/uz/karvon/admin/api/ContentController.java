package uz.karvon.admin.api;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.admin.api.dto.PublicBannerResponse;
import uz.karvon.admin.api.dto.PublicPageResponse;
import uz.karvon.admin.api.dto.PublicTickerMessageResponse;
import uz.karvon.admin.application.ContentService;
import uz.karvon.admin.application.TickerService;

import java.util.List;

/**
 * Public site content: home page banners, the top bar ticker and text pages (FAQ, delivery, returns, public offer).
 */
@Tag(name = "Content")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService content;
    private final TickerService ticker;

    @GetMapping("/banners")
    public List<PublicBannerResponse> banners() {
        return content.visibleBanners();
    }

    /** Messages of the running line in the top bar, in the language of the Accept-Language header. */
    @GetMapping("/ticker")
    public List<PublicTickerMessageResponse> ticker() {
        return ticker.visible();
    }

    /** In the language of the Accept-Language header (uz by default, or ru). */
    @GetMapping("/pages/{slug}")
    public PublicPageResponse page(@Parameter(example = "faq") @PathVariable String slug) {
        return content.publicPage(slug);
    }
}
