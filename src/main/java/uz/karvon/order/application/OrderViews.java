package uz.karvon.order.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.catalog.domain.TranslatedPair;
import uz.karvon.order.api.dto.OrderCommentResponse;
import uz.karvon.order.api.dto.OrderDetailResponse;
import uz.karvon.order.api.dto.OrderHistoryResponse;
import uz.karvon.order.api.dto.OrderItemResponse;
import uz.karvon.order.api.dto.OrderSummaryResponse;
import uz.karvon.order.domain.Order;
import uz.karvon.order.domain.OrderComment;
import uz.karvon.order.domain.OrderItem;
import uz.karvon.order.domain.OrderStatus;
import uz.karvon.order.domain.OrderStatusHistoryEntry;
import uz.karvon.order.domain.OrderTimeline;
import uz.karvon.user.api.dto.AddressResponse;

import java.time.Duration;
import java.util.List;

/**
 * Turns orders into API answers. Staff views also show the Taobao order number of each item.
 */
@Component
@RequiredArgsConstructor
public class OrderViews {

    private static final TypeReference<List<TranslatedPair>> PROPERTIES = new TypeReference<>() {
    };

    private final JsonMapper json;

    @Value("${karvon.orders.payment-timeout:30m}")
    private Duration paymentTimeout;

    public OrderDetailResponse detail(Order order, List<OrderStatusHistoryEntry> history, boolean staff) {
        AddressResponse address = order.getAddressSnapshot() == null ? null
                : json.readValue(order.getAddressSnapshot(), AddressResponse.class);
        return new OrderDetailResponse(order.getId(), order.getNumber(), order.getStatus(), order.getDeliveryMethod(),
                address, order.getPaymentMethod(), order.getPricingSettingsId(), order.getGoodsUzs(),
                order.getServiceFeeUzs(), order.getPrepaidShippingUzs(), order.getRealWeightKg(),
                order.getFinalShippingUzs(), order.getShippingDifferenceUzs(), order.getTotalUzs(), order.getPaidUzs(),
                order.getRefundedUzs(), order.amountDueUzs(), order.getCustomerComment(),
                order.getInternationalTrackingNumber(), order.getCreatedAt(), order.getPaidAt(), order.getDeliveredAt(),
                order.getStatus() == OrderStatus.AWAITING_PAYMENT && order.getCreatedAt() != null
                        ? order.getCreatedAt().plus(paymentTimeout) : null,
                order.getItems().stream().map(item -> item(item, staff)).toList(),
                OrderTimeline.build(order.getStatus(), history));
    }

    public OrderSummaryResponse summary(Order order) {
        OrderItem first = order.getItems().isEmpty() ? null : order.getItems().getFirst();
        return new OrderSummaryResponse(order.getNumber(), order.getStatus(), order.getTotalUzs(), order.amountDueUzs(),
                order.getItems().size(), first == null ? null : first.getTitle(), first == null ? null : first.getImageUrl(),
                order.getCreatedAt());
    }

    public OrderHistoryResponse history(OrderStatusHistoryEntry entry) {
        return new OrderHistoryResponse(entry.getFromStatus(), entry.getToStatus(), entry.getChangedByUserId(),
                entry.getChangedByRole(), entry.getComment(), entry.getCreatedAt());
    }

    public OrderCommentResponse comment(OrderComment comment) {
        return new OrderCommentResponse(comment.getId(), comment.getAuthorId(), comment.getText(), comment.getCreatedAt());
    }

    private OrderItemResponse item(OrderItem item, boolean staff) {
        return new OrderItemResponse(item.getId(), item.getExternalProductId(), item.getSkuId(), item.getTitle(),
                item.getImageUrl(), json.readValue(item.getSkuProperties(), PROPERTIES), item.getShopName(),
                item.getPriceCny(), item.getChinaShippingCny(), item.getWeightKg(), item.getQuantity(), item.getComment(),
                item.getGoodsUzs(), item.getServiceFeeUzs(), item.getShippingUzs(), item.getTotalUzs(), item.getStatus(),
                item.getActualPriceCny(), item.getChinaTrackingNumber(), staff ? item.getTaobaoOrderNumber() : null);
    }
}
