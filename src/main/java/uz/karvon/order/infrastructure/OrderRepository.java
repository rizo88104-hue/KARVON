package uz.karvon.order.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.karvon.order.domain.Order;
import uz.karvon.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByNumberAndCustomerId(String number, UUID customerId);

    @Query(value = "SELECT nextval('order_number_seq')", nativeQuery = true)
    long nextNumber();

    @Query("select o.id from Order o where o.status = :status and o.createdAt < :before")
    List<UUID> findIdsByStatusCreatedBefore(@Param("status") OrderStatus status, @Param("before") Instant before);
}
