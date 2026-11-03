package uz.karvon.cart.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import uz.karvon.cart.domain.Favorite;

import java.util.List;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {

    List<Favorite> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("select f.externalProductId from Favorite f where f.userId = :userId order by f.createdAt desc")
    List<String> findProductIds(UUID userId);

    boolean existsByUserIdAndExternalProductId(UUID userId, String externalProductId);

    long countByUserId(UUID userId);

    @Modifying
    @Query("delete from Favorite f where f.userId = :userId and f.externalProductId = :externalProductId")
    int deleteByUserIdAndProduct(UUID userId, String externalProductId);
}
