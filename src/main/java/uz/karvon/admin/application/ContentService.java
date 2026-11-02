package uz.karvon.admin.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.admin.api.dto.BannerRequest;
import uz.karvon.admin.api.dto.BannerResponse;
import uz.karvon.admin.api.dto.ContentPageRequest;
import uz.karvon.admin.api.dto.ContentPageResponse;
import uz.karvon.admin.api.dto.PublicBannerResponse;
import uz.karvon.admin.api.dto.PublicPageResponse;
import uz.karvon.admin.domain.Banner;
import uz.karvon.admin.domain.ContentPage;
import uz.karvon.admin.infrastructure.BannerRepository;
import uz.karvon.admin.infrastructure.ContentPageRepository;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.translation.domain.Lang;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Site content edited by ADMIN: home page banners and text pages.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentService {

    private final BannerRepository banners;
    private final ContentPageRepository pages;
    private final Clock clock;

    // --- banners

    public List<BannerResponse> banners() {
        return banners.findAllByOrderBySortOrderAscCreatedAtAsc().stream().map(ContentService::response).toList();
    }

    public BannerResponse banner(UUID id) {
        return response(loadBanner(id));
    }

    public Optional<BannerResponse> findBanner(UUID id) {
        return banners.findById(id).map(ContentService::response);
    }

    /** Active banners whose period includes now, in display order. */
    public List<PublicBannerResponse> visibleBanners() {
        return banners.findVisible(Instant.now(clock)).stream()
                .map(b -> new PublicBannerResponse(b.getId(), b.getTitle(), b.getSubtitle(), b.getImageUrl(), b.getLinkUrl()))
                .toList();
    }

    @Transactional
    public BannerResponse createBanner(BannerRequest request) {
        Banner banner = new Banner();
        apply(banner, request);
        return response(banners.save(banner));
    }

    @Transactional
    public BannerResponse updateBanner(UUID id, BannerRequest request) {
        Banner banner = loadBanner(id);
        apply(banner, request);
        return response(banners.saveAndFlush(banner));
    }

    @Transactional
    public void deleteBanner(UUID id) {
        banners.delete(loadBanner(id));
    }

    // --- pages

    public List<ContentPageResponse> pages() {
        return pages.findAllByOrderBySlugAsc().stream().map(ContentService::response).toList();
    }

    public ContentPageResponse page(UUID id) {
        return response(loadPage(id));
    }

    public Optional<ContentPageResponse> findPage(UUID id) {
        return pages.findById(id).map(ContentService::response);
    }

    /** A published page in the customer's language. */
    public PublicPageResponse publicPage(String slug) {
        ContentPage page = pages.findBySlug(slug).filter(ContentPage::isPublished)
                .orElseThrow(() -> new NotFoundException("PAGE_NOT_FOUND", "error.page.notFound"));
        boolean ru = Lang.ofCustomer(LocaleContextHolder.getLocale()) == Lang.RU;
        return new PublicPageResponse(page.getSlug(), ru ? "ru" : "uz", ru ? page.getTitleRu() : page.getTitleUz(),
                ru ? page.getBodyRu() : page.getBodyUz(), page.getUpdatedAt());
    }

    @Transactional
    public ContentPageResponse createPage(ContentPageRequest request) {
        if (pages.existsBySlug(request.slug())) {
            throw slugTaken();
        }
        ContentPage page = new ContentPage();
        apply(page, request);
        return response(pages.save(page));
    }

    @Transactional
    public ContentPageResponse updatePage(UUID id, ContentPageRequest request) {
        ContentPage page = loadPage(id);
        if (pages.existsBySlugAndIdNot(request.slug(), id)) {
            throw slugTaken();
        }
        apply(page, request);
        return response(pages.saveAndFlush(page));
    }

    @Transactional
    public void deletePage(UUID id) {
        pages.delete(loadPage(id));
    }

    private static void apply(Banner banner, BannerRequest request) {
        if (request.activeFrom() != null && request.activeTo() != null && !request.activeTo().isAfter(request.activeFrom())) {
            throw new BusinessException("BANNER_PERIOD_INVALID", "error.banner.periodInvalid");
        }
        banner.setTitle(request.title().trim());
        banner.setSubtitle(blankToNull(request.subtitle()));
        banner.setImageUrl(request.imageUrl().trim());
        banner.setLinkUrl(blankToNull(request.linkUrl()));
        banner.setSortOrder(request.sortOrder());
        banner.setActive(request.active());
        banner.setActiveFrom(request.activeFrom());
        banner.setActiveTo(request.activeTo());
    }

    private static void apply(ContentPage page, ContentPageRequest request) {
        page.setSlug(request.slug());
        page.setTitleUz(request.titleUz().trim());
        page.setTitleRu(request.titleRu().trim());
        page.setBodyUz(request.bodyUz());
        page.setBodyRu(request.bodyRu());
        page.setPublished(request.published());
    }

    private Banner loadBanner(UUID id) {
        return banners.findById(id).orElseThrow(() -> new NotFoundException("BANNER_NOT_FOUND", "error.banner.notFound"));
    }

    private ContentPage loadPage(UUID id) {
        return pages.findById(id).orElseThrow(() -> new NotFoundException("PAGE_NOT_FOUND", "error.page.notFound"));
    }

    private static BusinessException slugTaken() {
        return new BusinessException(HttpStatus.CONFLICT, "PAGE_SLUG_TAKEN", "error.page.slugTaken");
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static BannerResponse response(Banner b) {
        return new BannerResponse(b.getId(), b.getTitle(), b.getSubtitle(), b.getImageUrl(), b.getLinkUrl(),
                b.getSortOrder(), b.isActive(), b.getActiveFrom(), b.getActiveTo(), b.getCreatedAt(), b.getUpdatedAt());
    }

    private static ContentPageResponse response(ContentPage p) {
        return new ContentPageResponse(p.getId(), p.getSlug(), p.getTitleUz(), p.getTitleRu(), p.getBodyUz(),
                p.getBodyRu(), p.isPublished(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
