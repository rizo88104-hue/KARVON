package uz.karvon.admin.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.admin.api.dto.BannerRequest;
import uz.karvon.admin.api.dto.BannerResponse;
import uz.karvon.admin.api.dto.ContentPageRequest;
import uz.karvon.admin.api.dto.ContentPageResponse;
import uz.karvon.admin.api.dto.TickerMessageRequest;
import uz.karvon.admin.api.dto.TickerMessageResponse;
import uz.karvon.admin.application.ContentService;
import uz.karvon.admin.application.TickerService;
import uz.karvon.common.api.Audited;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin: content")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminContentController {

    private final ContentService content;
    private final TickerService ticker;

    @GetMapping("/banners")
    public List<BannerResponse> banners() {
        return content.banners();
    }

    @GetMapping("/banners/{id}")
    public BannerResponse banner(@PathVariable UUID id) {
        return content.banner(id);
    }

    @PostMapping("/banners")
    @ResponseStatus(HttpStatus.CREATED)
    @Audited(action = "BANNER_CREATE", entityType = "BANNER")
    public BannerResponse createBanner(@Valid @RequestBody BannerRequest request) {
        return content.createBanner(request);
    }

    @PutMapping("/banners/{id}")
    @Audited(action = "BANNER_UPDATE", entityType = "BANNER")
    public BannerResponse updateBanner(@PathVariable UUID id, @Valid @RequestBody BannerRequest request) {
        return content.updateBanner(id, request);
    }

    @DeleteMapping("/banners/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Audited(action = "BANNER_DELETE", entityType = "BANNER")
    public void deleteBanner(@PathVariable UUID id) {
        content.deleteBanner(id);
    }

    @GetMapping("/ticker")
    public List<TickerMessageResponse> tickerMessages() {
        return ticker.list();
    }

    @GetMapping("/ticker/{id}")
    public TickerMessageResponse tickerMessage(@PathVariable UUID id) {
        return ticker.get(id);
    }

    @PostMapping("/ticker")
    @ResponseStatus(HttpStatus.CREATED)
    @Audited(action = "TICKER_CREATE", entityType = "TICKER")
    public TickerMessageResponse createTickerMessage(@Valid @RequestBody TickerMessageRequest request) {
        return ticker.create(request);
    }

    @PutMapping("/ticker/{id}")
    @Audited(action = "TICKER_UPDATE", entityType = "TICKER")
    public TickerMessageResponse updateTickerMessage(@PathVariable UUID id, @Valid @RequestBody TickerMessageRequest request) {
        return ticker.update(id, request);
    }

    @DeleteMapping("/ticker/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Audited(action = "TICKER_DELETE", entityType = "TICKER")
    public void deleteTickerMessage(@PathVariable UUID id) {
        ticker.delete(id);
    }

    @GetMapping("/pages")
    public List<ContentPageResponse> pages() {
        return content.pages();
    }

    @GetMapping("/pages/{id}")
    public ContentPageResponse page(@PathVariable UUID id) {
        return content.page(id);
    }

    @PostMapping("/pages")
    @ResponseStatus(HttpStatus.CREATED)
    @Audited(action = "PAGE_CREATE", entityType = "PAGE")
    public ContentPageResponse createPage(@Valid @RequestBody ContentPageRequest request) {
        return content.createPage(request);
    }

    @PutMapping("/pages/{id}")
    @Audited(action = "PAGE_UPDATE", entityType = "PAGE")
    public ContentPageResponse updatePage(@PathVariable UUID id, @Valid @RequestBody ContentPageRequest request) {
        return content.updatePage(id, request);
    }

    @DeleteMapping("/pages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Audited(action = "PAGE_DELETE", entityType = "PAGE")
    public void deletePage(@PathVariable UUID id) {
        content.deletePage(id);
    }
}
