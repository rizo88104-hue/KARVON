package uz.karvon.order.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.karvon.order.domain.OrderStatusHistoryEntry;

import java.util.List;
import java.util.UUID;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntry, UUID> {

    List<OrderStatusHistoryEntry> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
