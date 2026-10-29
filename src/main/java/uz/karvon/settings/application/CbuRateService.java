package uz.karvon.settings.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.karvon.settings.api.dto.CbuRateResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shows ADMIN how our customer rate compares to the official one.
 */
@Service
@RequiredArgsConstructor
public class CbuRateService {

    private final CbuRateClient client;
    private final PricingSettingsService settings;

    public CbuRateResponse compare() {
        CbuRateClient.CbuRate cbu = client.cnyRate();
        BigDecimal ours = settings.current().customerRate();
        BigDecimal difference = ours.subtract(cbu.rate())
                .multiply(BigDecimal.valueOf(100))
                .divide(cbu.rate(), 2, RoundingMode.HALF_UP);
        return new CbuRateResponse("CNY", cbu.rate(), cbu.date(), ours, difference);
    }
}
