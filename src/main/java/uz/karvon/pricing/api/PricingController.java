package uz.karvon.pricing.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.pricing.api.dto.EstimateRequest;
import uz.karvon.pricing.application.PricingService;
import uz.karvon.pricing.domain.PriceBreakdown;

@Tag(name = "Pricing")
@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricing;

    /** Public price estimate with the active settings. */
    @PostMapping("/estimate")
    public PriceBreakdown estimate(@Valid @RequestBody EstimateRequest request) {
        return pricing.estimate(request.priceCny(), request.chinaShippingCny(),
                request.quantity() == null ? 1 : request.quantity(), request.weightKg());
    }
}
