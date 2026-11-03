package uz.karvon.admin.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.karvon.admin.application.ContentService;
import uz.karvon.admin.application.TickerService;
import uz.karvon.common.application.AuditSnapshotSource;

import java.util.Optional;
import java.util.UUID;

/**
 * "Before" states of banners, pages and ticker messages for the audit log.
 */
@Configuration
public class ContentAuditSnapshots {

    @Bean
    AuditSnapshotSource bannerSnapshots(ContentService content) {
        return AuditSnapshotSource.of("BANNER", id -> id == null ? Optional.empty() : content.findBanner(UUID.fromString(id)));
    }

    @Bean
    AuditSnapshotSource tickerSnapshots(TickerService ticker) {
        return AuditSnapshotSource.of("TICKER", id -> id == null ? Optional.empty() : ticker.find(UUID.fromString(id)));
    }

    @Bean
    AuditSnapshotSource pageSnapshots(ContentService content) {
        return AuditSnapshotSource.of("PAGE", id -> id == null ? Optional.empty() : content.findPage(UUID.fromString(id)));
    }
}
