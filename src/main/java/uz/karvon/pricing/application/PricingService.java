package uz.karvon.pricing.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.karvon.pricing.domain.PriceBreakdown;
import uz.karvon.pricing.domain.PriceCalculator;
import uz.karvon.pricing.domain.PricingParameters;
import uz.karvon.pricing.domain.ShippingAdjustment;
import uz.karvon.settings.api.dto.PricingSettingsResponse;
import uz.karvon.settings.application.PricingSettingsService;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Prices items with the active settings.
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private final PricingSettingsService settings;
    private final PriceCalculator calculator = new PriceCalculator();

    public PriceBreakdown estimate(BigDecimal priceCny, BigDecimal chinaShippingCny, int quantity, BigDecimal weightKg) {
        return quoter().price(priceCny, chinaShippingCny, quantity, weightKg);
    }

    /** A calculator bound to the active settings, read once. */
    public PriceQuoter quoter() {
        return new PriceQuoter(calculator, toParameters(settings.current()));
    }

    /**
     * Shipping for the real weight, with the settings version an order was priced with.
     * {@code realWeightKg} is the weight of all {@code quantity} pieces together.
     */
    public ShippingAdjustment shippingAdjustment(UUID settingsId, BigDecimal prepaidShippingUzs, BigDecimal realWeightKg,
                                                 int quantity) {
        return ShippingAdjustment.of(prepaidShippingUzs, realWeightKg, quantity, toParameters(settings.getById(settingsId)));
    }

    static PricingParameters toParameters(PricingSettingsResponse s) {
        return new PricingParameters(s.id(), s.customerRate(), s.feePercent(), s.minServiceFee(),
                s.shippingCostPerKg(), s.ourMarkupPerKg(), s.minWeightKg(), s.defaultWeightKg());
    }
}
