package uz.karvon.admin.api.dto;

import uz.karvon.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Numbers of orders created in {@code from}..{@code to} (days in Asia/Tashkent, both included).
 * <ul>
 * <li>{@code revenueUzs}: paid minus refunded;</li>
 * <li>{@code serviceIncomeUzs}: service fees of paid, not cancelled orders;</li>
 * <li>{@code shippingIncomeUzs}: our per-kg markup in the shipping of paid, not cancelled orders;</li>
 * <li>{@code averageOrderUzs}: revenue / number of paid orders;</li>
 * <li>{@code ordersByStatus}: every status, also those with 0;</li>
 * <li>{@code ordersToday} and {@code delayedCount} (over the SLA now) do not depend on the period.</li>
 * </ul>
 */
public record DashboardResponse(
        LocalDate from,
        LocalDate to,
        long ordersToday,
        long ordersInPeriod,
        BigDecimal revenueUzs,
        BigDecimal serviceIncomeUzs,
        BigDecimal shippingIncomeUzs,
        BigDecimal averageOrderUzs,
        Map<OrderStatus, Long> ordersByStatus,
        long delayedCount,
        List<DailyPoint> dailySeries) {

    /** One day of the period, also days without orders. */
    public record DailyPoint(LocalDate date, long orders, BigDecimal revenue) {
    }
}
