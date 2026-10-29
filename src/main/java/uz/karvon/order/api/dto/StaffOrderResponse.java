package uz.karvon.order.api.dto;

import uz.karvon.order.domain.OrderStatus;
import uz.karvon.user.api.dto.UserResponse;

import java.util.List;
import java.util.Set;

/**
 * An order for staff: everything the customer sees, the customer, the full history, internal notes
 * and the statuses it can go to next.
 */
public record StaffOrderResponse(
        OrderDetailResponse order,
        UserResponse customer,
        List<OrderHistoryResponse> history,
        List<OrderCommentResponse> comments,
        Set<OrderStatus> nextStatuses) {
}
