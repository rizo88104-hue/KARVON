package uz.karvon.wallet.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.karvon.wallet.domain.Wallet;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(UUID userId);

    /** SELECT ... FOR UPDATE: changes of one wallet run one after another. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId = :userId")
    Optional<Wallet> lockByUserId(@Param("userId") UUID userId);

    /** Creates the empty wallet if the user has none; safe when two requests do it at once. */
    @Modifying
    @Query(value = """
            INSERT INTO wallets (id, user_id, balance_uzs, created_at, updated_at, version)
            VALUES (:id, :userId, 0, now(), now(), 0)
            ON CONFLICT (user_id) DO NOTHING""", nativeQuery = true)
    void createIfMissing(@Param("id") UUID id, @Param("userId") UUID userId);
}
