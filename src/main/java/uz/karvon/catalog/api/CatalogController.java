package uz.karvon.catalog.api;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.catalog.api.dto.CategoryResponse;
import uz.karvon.catalog.api.dto.ProductCardResponse;
import uz.karvon.catalog.api.dto.ProductDetailResponse;
import uz.karvon.catalog.api.dto.ResolveLinkRequest;
import uz.karvon.catalog.api.dto.ResolveLinkResponse;
import uz.karvon.catalog.application.CatalogService;
import uz.karvon.catalog.domain.SortOrder;
import uz.karvon.common.api.PageResponse;
import uz.karvon.translation.domain.Lang;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public catalog. Texts come in the language of the Accept-Language header (uz by default, or ru).
 */
@Tag(name = "Catalog")
@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalog;

    /** {@code q} in Uzbek, Russian or Chinese; {@code minPrice}/{@code maxPrice} limit the goods price in UZS. */
    @GetMapping("/search")
    public PageResponse<ProductCardResponse> search(
            @Parameter(example = "krossovka") @RequestParam @NotBlank @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) @Max(99) int page,
            @RequestParam(defaultValue = "RELEVANCE") SortOrder sort,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal maxPrice) {
        return catalog.search(q, page, sort, minPrice, maxPrice, lang());
    }

    @GetMapping("/products/{externalId}")
    public ProductDetailResponse product(@Parameter(example = "680000001117") @PathVariable String externalId) {
        return catalog.product(externalId, lang());
    }

    /** Accepts a product link or the whole share text from the Taobao app. */
    @PostMapping("/resolve-link")
    public ResolveLinkResponse resolveLink(@Valid @RequestBody ResolveLinkRequest request) {
        return new ResolveLinkResponse(catalog.resolveLink(request.url()));
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalog.categories();
    }

    private static Lang lang() {
        return Lang.ofCustomer(LocaleContextHolder.getLocale());
    }
}
