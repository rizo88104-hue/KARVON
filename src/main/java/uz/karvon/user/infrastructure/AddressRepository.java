package uz.karvon.user.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.karvon.user.domain.Address;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<Address> findByIdAndUserId(UUID id, UUID userId);

    Optional<Address> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserId(UUID userId);

    /** Run before marking another address as default, so the unique "one default" index is never broken. */
    @Modifying
    @Query("update Address a set a.defaultAddress = false where a.userId = :userId and a.defaultAddress = true")
    void clearDefault(@Param("userId") UUID userId);
}
