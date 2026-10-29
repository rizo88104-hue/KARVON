package uz.karvon.settings.application;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uz.karvon.settings.api.dto.PricingSettingsResponse;
import uz.karvon.settings.api.dto.PublicPricingSettingsResponse;
import uz.karvon.settings.domain.PricingSettings;

@Mapper
public interface SettingsMapper {

    PricingSettingsResponse toResponse(PricingSettings settings);

    @Mapping(target = "shippingPricePerKg",
            expression = "java(settings.shippingCostPerKg().add(settings.ourMarkupPerKg()))")
    PublicPricingSettingsResponse toPublic(PricingSettingsResponse settings);
}
