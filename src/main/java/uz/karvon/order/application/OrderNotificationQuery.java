package uz.karvon.order.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.order.infrastructure.OrderRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Order details for the notification module (weight, tracking number, pickup info...).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderNotificationQuery {

    private final OrderRepository orders;

    public Optional<OrderNotificationFacts> facts(UUID orderId) {
        return orders.findById(orderId).map(order -> new OrderNotificationFacts(order.getNumber(),
                order.getRealWeightKg(), order.getShippingDifferenceUzs(), order.getInternationalTrackingNumber(),
                order.getPickupInfo()));
    }
}
