package uz.karvon.order.api.dto;

import uz.karvon.catalog.domain.TranslatedPair;
import uz.karvon.order.domain.OrderItemStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * An order line with its price at order time. {@code taobaoOrderNumber} is filled only for staff.
 */
public record OrderItemResponse(
        UUID id,
        String externalProductId,
        String skuId,
        String title,
        String imageUrl,
        List<TranslatedPair> skuProperties,
        String shopName,
        BigDecimal priceCny,
        BigDecimal chinaShippingCny,
        BigDecimal weightKg,
        int quantity,
        String comment,
        BigDecimal goodsUzs,
        BigDecimal serviceFeeUzs,
        BigDecimal shippingUzs,
        BigDecimal totalUzs,
        OrderItemStatus status,
        BigDecimal actualPriceCny,
        String chinaTrackingNumber,
        String taobaoOrderNumber) {
}
