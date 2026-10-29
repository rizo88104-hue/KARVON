package uz.karvon.catalog.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.catalog.api.dto.BlockedKeywordRequest;
import uz.karvon.catalog.api.dto.BlockedKeywordResponse;
import uz.karvon.catalog.application.BlockedGoodsService;
import uz.karvon.catalog.domain.BlockedCategory;
import uz.karvon.common.api.PageResponse;
import uz.karvon.translation.domain.Lang;

import java.util.UUID;

@Tag(name = "Admin: blocked keywords")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/blocked-keywords")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminBlockedKeywordController {

    private final BlockedGoodsService blockedGoods;

    /** {@code q} matches any part of the keyword. */
    @GetMapping
    public PageResponse<BlockedKeywordResponse> list(@RequestParam(required = false) Lang lang,
                                                     @RequestParam(required = false) BlockedCategory category,
                                                     @RequestParam(required = false) String q,
                                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                                     @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return blockedGoods.list(lang, category, q, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BlockedKeywordResponse create(@Valid @RequestBody BlockedKeywordRequest request) {
        return blockedGoods.create(request);
    }

    @PutMapping("/{id}")
    public BlockedKeywordResponse update(@PathVariable UUID id, @Valid @RequestBody BlockedKeywordRequest request) {
        return blockedGoods.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        blockedGoods.delete(id);
    }
}
