package uz.karvon.order.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.karvon.order.domain.OrderItem;

import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
}
