package uz.karvon.order.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.karvon.order.domain.OrderComment;

import java.util.List;
import java.util.UUID;

public interface OrderCommentRepository extends JpaRepository<OrderComment, UUID> {

    List<OrderComment> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
