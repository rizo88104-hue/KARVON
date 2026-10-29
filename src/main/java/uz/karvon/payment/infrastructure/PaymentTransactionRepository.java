package uz.karvon.payment.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.karvon.order.application.PaymentPurpose;
import uz.karvon.payment.domain.PaymentProvider;
import uz.karvon.payment.domain.PaymentState;
import uz.karvon.payment.domain.PaymentTransaction;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    /** Locked, so repeated or parallel provider calls for one transaction run one after another. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PaymentTransaction t where t.provider = :provider and t.providerTransactionId = :id")
    Optional<PaymentTransaction> lockByProviderId(@Param("provider") PaymentProvider provider, @Param("id") String id);

    Optional<PaymentTransaction> findByProviderAndProviderTransactionId(PaymentProvider provider, String providerTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PaymentTransaction t where t.provider = :provider and t.number = :number")
    Optional<PaymentTransaction> lockByNumber(@Param("provider") PaymentProvider provider, @Param("number") long number);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PaymentTransaction t where t.id = :id")
    Optional<PaymentTransaction> lockById(@Param("id") UUID id);

    boolean existsByOrderIdAndPurposeAndProviderAndState(UUID orderId, PaymentPurpose purpose, PaymentProvider provider,
                                                         PaymentState state);

    /** A provider payment still running for the order (the dev/test fake checkout does not count). */
    @Query("""
            select count(t) > 0 from PaymentTransaction t
            where t.orderId = :orderId and t.provider = :provider and t.state = :state and t.createTime > :after
              and t.providerTransactionId not like 'fake-%'""")
    boolean existsPending(@Param("orderId") UUID orderId, @Param("provider") PaymentProvider provider,
                          @Param("state") PaymentState state, @Param("after") Instant after);

    /** Payme GetStatement: by creation time in Payme Business, from and to inclusive, oldest first. */
    List<PaymentTransaction> findByProviderAndProviderTimeBetweenOrderByProviderTimeAsc(PaymentProvider provider,
                                                                                         Long from, Long to);

    @Query(value = "SELECT nextval('payment_number_seq')", nativeQuery = true)
    long nextNumber();
}
