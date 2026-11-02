package uz.karvon.admin.api.dto;

import uz.karvon.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One order of the orders report; how the money is counted is described in OrderProfit.
 * {@code date} is the creation time in Asia/Tashkent. {@code totalPaidUzs} is paid minus refunded,
 * {@code shippingCostUzs} is the cargo cost (the shipping without our markup).
 */
public record OrderReportRow(
        String number,
        LocalDateTime date,
        String customerPhone,
        OrderStatus status,
        BigDecimal totalPaidUzs,
        BigDecimal goodsCostUzs,
        BigDecimal shippingCostUzs,
        BigDecimal serviceFeeUzs,
        BigDecimal profitUzs) {
}
