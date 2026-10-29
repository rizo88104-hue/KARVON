package uz.karvon.pricing.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.karvon.pricing.domain.PriceBreakdown;
import uz.karvon.pricing.domain.PriceCalculator;
import uz.karvon.pricing.domain.PricingParameters;
import uz.karvon.settings.api.dto.PricingSettingsResponse;
import uz.karvon.settings.application.PricingSettingsService;

import java.math.BigDecimal;

/**
 * Prices items with the active settings.
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private final PricingSettingsService settings;
    private final PriceCalculator calculator = new PriceCalculator();

    public PriceBreakdown estimate(BigDecimal priceCny, BigDecimal chinaShippingCny, int quantity, BigDecimal weightKg) {
        return calculator.calculate(priceCny, chinaShippingCny, quantity, weightKg, toParameters(settings.current()));
    }

    static PricingParameters toParameters(PricingSettingsResponse s) {
        return new PricingParameters(s.id(), s.customerRate(), s.feePercent(), s.minServiceFee(),
                s.shippingCostPerKg(), s.ourMarkupPerKg(), s.minWeightKg(), s.defaultWeightKg());
    }
}
