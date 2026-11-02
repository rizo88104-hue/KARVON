package uz.karvon.pricing.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.karvon.pricing.domain.PromoCodeUsage;

import java.util.Optional;
import java.util.UUID;

public interface PromoCodeUsageRepository extends JpaRepository<PromoCodeUsage, UUID> {

    long countByPromoCodeIdAndUserId(UUID promoCodeId, UUID userId);

    Optional<PromoCodeUsage> findByOrderId(UUID orderId);
}
