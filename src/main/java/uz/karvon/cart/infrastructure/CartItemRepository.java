package uz.karvon.cart.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.karvon.cart.domain.CartItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    List<CartItem> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<CartItem> findByIdAndUserId(UUID id, UUID userId);

    Optional<CartItem> findByUserIdAndExternalProductIdAndSkuId(UUID userId, String externalProductId, String skuId);

    long countByUserId(UUID userId);

    List<CartItem> findByUserIdAndSelected(UUID userId, boolean selected);
}
