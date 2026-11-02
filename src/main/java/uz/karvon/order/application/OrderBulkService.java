package uz.karvon.order.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.karvon.common.application.BusinessException;
import uz.karvon.order.api.dto.BulkStatusResult;
import uz.karvon.order.domain.OrderStatus;
import uz.karvon.user.domain.Role;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One status change for many orders that travel together (e.g. one cargo shipment). Each order is changed in its own
 * transaction with the same rules as one by one, so an order that cannot move (e.g. unpaid shipping difference) is
 * reported and the others still move.
 */
@Service
@RequiredArgsConstructor
public class OrderBulkService {

    /** Steps that need no data per order besides a shared tracking number. */
    static final Set<OrderStatus> ALLOWED = EnumSet.of(OrderStatus.IN_TRANSIT, OrderStatus.ARRIVED, OrderStatus.DELIVERED);
    public static final int MAX_ORDERS = 200;

    private final StaffOrderService staffOrders;

    public List<BulkStatusResult> change(UUID staffId, Set<Role> roles, List<UUID> orderIds, OrderStatus to,
                                         String trackingNumber) {
        if (!ALLOWED.contains(to)) {
            throw new BusinessException("BULK_STATUS_NOT_ALLOWED", "error.order.bulkStatusNotAllowed");
        }
        List<BulkStatusResult> results = new ArrayList<>();
        for (UUID orderId : new LinkedHashSet<>(orderIds)) {
            try {
                OrderStatus status = staffOrders.changeOne(staffId, roles, orderId, to, trackingNumber);
                results.add(new BulkStatusResult(orderId, true, status, null));
            } catch (BusinessException ex) {
                results.add(new BulkStatusResult(orderId, false, null, ex.getCode()));
            }
        }
        return results;
    }
}
