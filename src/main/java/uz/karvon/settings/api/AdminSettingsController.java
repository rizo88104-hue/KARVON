package uz.karvon.settings.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.common.api.CurrentUser;
import uz.karvon.common.api.PageResponse;
import uz.karvon.settings.api.dto.CbuRateResponse;
import uz.karvon.settings.api.dto.PricingSettingsResponse;
import uz.karvon.settings.api.dto.UpdatePricingSettingsRequest;
import uz.karvon.settings.application.CbuRateService;
import uz.karvon.settings.application.PricingSettingsService;

@Tag(name = "Admin: settings")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final PricingSettingsService settings;
    private final CbuRateService cbuRates;

    @GetMapping("/pricing")
    public PricingSettingsResponse pricing() {
        return settings.current();
    }

    /** Saves a new version that becomes active at once. Earlier versions are kept unchanged. */
    @PutMapping("/pricing")
    public PricingSettingsResponse update(Authentication authentication,
                                          @Valid @RequestBody UpdatePricingSettingsRequest request) {
        return settings.update(CurrentUser.id(authentication), request);
    }

    @GetMapping("/pricing/history")
    public PageResponse<PricingSettingsResponse> history(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return settings.history(page, size);
    }

    /** The official Central Bank CNY rate, for reference. Our rate is still set by hand. */
    @GetMapping("/cbu-rate")
    public CbuRateResponse cbuRate() {
        return cbuRates.compare();
    }
}
