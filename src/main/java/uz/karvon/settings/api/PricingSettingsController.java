package uz.karvon.settings.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.settings.api.dto.PublicPricingSettingsResponse;
import uz.karvon.settings.application.PricingSettingsService;

@Tag(name = "Settings")
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class PricingSettingsController {

    private final PricingSettingsService settings;

    /** Public; served from the Redis cache. */
    @GetMapping("/pricing")
    public PublicPricingSettingsResponse pricing() {
        return settings.currentPublic();
    }
}
