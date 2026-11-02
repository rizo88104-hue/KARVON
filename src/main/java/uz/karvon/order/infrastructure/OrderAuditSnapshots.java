package uz.karvon.order.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.karvon.common.application.AuditSnapshotSource;
import uz.karvon.order.application.StaffOrderService;

import java.util.Optional;
import java.util.UUID;

/**
 * "Before" states of orders for the audit log (the staff view of the order).
 */
@Configuration
public class OrderAuditSnapshots {

    @Bean
    AuditSnapshotSource orderSnapshots(StaffOrderService orders) {
        return AuditSnapshotSource.of("ORDER", id -> id == null ? Optional.empty() : Optional.of(orders.get(UUID.fromString(id))));
    }
}
