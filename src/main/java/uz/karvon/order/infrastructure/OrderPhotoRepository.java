package uz.karvon.order.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.karvon.order.domain.OrderPhoto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderPhotoRepository extends JpaRepository<OrderPhoto, UUID> {

    List<OrderPhoto> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    Optional<OrderPhoto> findByIdAndOrderId(UUID id, UUID orderId);

    long countByOrderId(UUID orderId);
}
