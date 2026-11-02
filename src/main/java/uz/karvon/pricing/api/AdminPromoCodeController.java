package uz.karvon.pricing.api;

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
import uz.karvon.common.api.Audited;
import uz.karvon.common.api.PageResponse;
import uz.karvon.pricing.api.dto.PromoCodeRequest;
import uz.karvon.pricing.api.dto.PromoCodeResponse;
import uz.karvon.pricing.application.PromoCodeService;

import java.util.UUID;

@Tag(name = "Admin: promo codes")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/promo-codes")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPromoCodeController {

    private final PromoCodeService promoCodes;

    /** {@code q} matches any part of the code. */
    @GetMapping
    public PageResponse<PromoCodeResponse> list(@RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return promoCodes.list(q, page, size);
    }

    @GetMapping("/{id}")
    public PromoCodeResponse get(@PathVariable UUID id) {
        return promoCodes.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Audited(action = "PROMO_CODE_CREATE", entityType = "PROMO_CODE")
    public PromoCodeResponse create(@Valid @RequestBody PromoCodeRequest request) {
        return promoCodes.create(request);
    }

    @PutMapping("/{id}")
    @Audited(action = "PROMO_CODE_UPDATE", entityType = "PROMO_CODE")
    public PromoCodeResponse update(@PathVariable UUID id, @Valid @RequestBody PromoCodeRequest request) {
        return promoCodes.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Audited(action = "PROMO_CODE_DELETE", entityType = "PROMO_CODE")
    public void delete(@PathVariable UUID id) {
        promoCodes.delete(id);
    }
}
