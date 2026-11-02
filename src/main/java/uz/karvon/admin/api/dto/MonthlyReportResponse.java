package uz.karvon.admin.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Orders created in each month of {@code year} (Asia/Tashkent): all 12 months, also empty ones.
 * Revenue is paid minus refunded; profit as in the orders report.
 */
public record MonthlyReportResponse(int year, List<Month> months, BigDecimal revenueUzs, BigDecimal profitUzs, long orders) {

    public record Month(int month, long orders, BigDecimal revenueUzs, BigDecimal profitUzs) {
    }
}
