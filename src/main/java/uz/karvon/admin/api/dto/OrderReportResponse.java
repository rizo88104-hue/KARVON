package uz.karvon.admin.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Orders created in {@code from}..{@code to} (Asia/Tashkent days), oldest first, with the column totals.
 */
public record OrderReportResponse(LocalDate from, LocalDate to, List<OrderReportRow> rows, Totals totals) {

    public record Totals(long orders, BigDecimal totalPaidUzs, BigDecimal goodsCostUzs, BigDecimal shippingCostUzs,
                         BigDecimal serviceFeeUzs, BigDecimal profitUzs) {
    }
}
