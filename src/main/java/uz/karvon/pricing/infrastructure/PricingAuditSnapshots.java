package uz.karvon.pricing.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.karvon.common.application.AuditSnapshotSource;
import uz.karvon.pricing.application.PromoCodeService;

import java.util.Optional;
import java.util.UUID;

/**
 * "Before" states of promo codes for the audit log.
 */
@Configuration
public class PricingAuditSnapshots {

    @Bean
    AuditSnapshotSource promoCodeSnapshots(PromoCodeService promoCodes) {
        return AuditSnapshotSource.of("PROMO_CODE", id -> id == null ? Optional.empty() : promoCodes.find(UUID.fromString(id)));
    }
}
