package uz.karvon.catalog.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.karvon.catalog.application.CategoryService;
import uz.karvon.common.application.AuditSnapshotSource;

import java.util.Optional;
import java.util.UUID;

/**
 * "Before" states of categories for the audit log.
 */
@Configuration
public class CatalogAuditSnapshots {

    @Bean
    AuditSnapshotSource categorySnapshots(CategoryService categories) {
        return AuditSnapshotSource.of("CATEGORY", id -> id == null ? Optional.empty() : categories.find(UUID.fromString(id)));
    }
}
