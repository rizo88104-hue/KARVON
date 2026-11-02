package uz.karvon.settings.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.karvon.common.application.AuditSnapshotSource;
import uz.karvon.settings.application.PricingSettingsService;
import uz.karvon.settings.application.SlaSettingsService;

import java.util.Optional;

/**
 * "Before" states of the active pricing and SLA settings for the audit log.
 */
@Configuration
public class SettingsAuditSnapshots {

    @Bean
    AuditSnapshotSource pricingSettingsSnapshots(PricingSettingsService settings) {
        return AuditSnapshotSource.of("PRICING_SETTINGS", id -> Optional.of(settings.current()));
    }

    @Bean
    AuditSnapshotSource slaSettingsSnapshots(SlaSettingsService sla) {
        return AuditSnapshotSource.of("SLA_SETTINGS", id -> Optional.of(sla.current()));
    }
}
