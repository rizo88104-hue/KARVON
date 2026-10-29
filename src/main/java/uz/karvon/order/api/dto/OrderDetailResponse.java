package uz.karvon.order.api.dto;

import uz.karvon.order.domain.DeliveryMethod;
import uz.karvon.order.domain.OrderStatus;
import uz.karvon.order.domain.OrderTimeline;
import uz.karvon.order.domain.PaymentMethod;
import uz.karvon.user.api.dto.AddressResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An order with items, money and the timeline. {@code amountDueUzs} is what is still to pay
 * (the order total until paid, then a positive shipping difference). {@code paymentDeadline} is set while
 * the order waits for payment; after it the order is cancelled automatically.
 */
public record OrderDetailResponse(
        UUID id,
        String number,
        OrderStatus status,
        DeliveryMethod deliveryMethod,
        AddressResponse address,
        PaymentMethod paymentMethod,
        UUID pricingSettingsId,
        BigDecimal goodsUzs,
        BigDecimal serviceFeeUzs,
        BigDecimal prepaidShippingUzs,
        BigDecimal realWeightKg,
        BigDecimal finalShippingUzs,
        BigDecimal shippingDifferenceUzs,
        BigDecimal totalUzs,
        BigDecimal paidUzs,
        BigDecimal refundedUzs,
        BigDecimal amountDueUzs,
        String customerComment,
        String internationalTrackingNumber,
        Instant createdAt,
        Instant paidAt,
        Instant deliveredAt,
        Instant paymentDeadline,
        List<OrderItemResponse> items,
        List<OrderTimeline.Step> timeline) {
}
