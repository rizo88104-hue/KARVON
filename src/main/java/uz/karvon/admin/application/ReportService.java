package uz.karvon.admin.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.admin.api.dto.DashboardResponse;
import uz.karvon.admin.api.dto.MonthlyReportResponse;
import uz.karvon.admin.api.dto.OrderReportResponse;
import uz.karvon.admin.api.dto.OrderReportRow;
import uz.karvon.admin.domain.OrderProfit;
import uz.karvon.admin.domain.ReportPeriod;
import uz.karvon.admin.infrastructure.ReportQueries;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.domain.Money;
import uz.karvon.order.application.DelayedOrdersService;
import uz.karvon.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The staff dashboard and the money reports. Days and months are counted in the business time zone
 * (karvon.reports.zone, Asia/Tashkent by default). How the profit is counted is described in {@link OrderProfit}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportQueries queries;
    private final DelayedOrdersService delayedOrders;
    private final Clock clock;

    @Value("${karvon.reports.zone:Asia/Tashkent}")
    private ZoneId zone;

    public DashboardResponse dashboard(LocalDate from, LocalDate to) {
        ReportPeriod period = period(from, to);
        ReportPeriod today = ReportPeriod.of(today(), today(), today(), zone);
        ReportQueries.Totals totals = queries.totals(period.start(), period.end());
        BigDecimal average = totals.paidOrders() == 0 ? BigDecimal.ZERO
                : Money.roundUzs(totals.revenue().divide(BigDecimal.valueOf(totals.paidOrders()), 2, RoundingMode.HALF_UP));

        Map<LocalDate, ReportQueries.Day> byDay = queries.daily(period.start(), period.end(), zone).stream()
                .collect(Collectors.toMap(ReportQueries.Day::date, Function.identity()));
        List<DashboardResponse.DailyPoint> series = new ArrayList<>();
        for (LocalDate day = period.from(); !day.isAfter(period.to()); day = day.plusDays(1)) {
            ReportQueries.Day found = byDay.get(day);
            series.add(new DashboardResponse.DailyPoint(day, found == null ? 0 : found.orders(),
                    found == null ? BigDecimal.ZERO : found.revenue()));
        }
        Map<OrderStatus, Long> byStatus = queries.countByStatus(period.start(), period.end());
        return new DashboardResponse(period.from(), period.to(), queries.ordersCreated(today.start(), today.end()),
                totals.orders(), totals.revenue(), totals.serviceIncome(), totals.shippingIncome(), average, byStatus,
                delayedOrders.delayed().size(), series);
    }

    public OrderReportResponse orders(LocalDate from, LocalDate to) {
        ReportPeriod period = period(from, to);
        List<OrderReportRow> rows = queries.orders(period.start(), period.end()).stream().map(this::row).toList();
        return new OrderReportResponse(period.from(), period.to(), rows, new OrderReportResponse.Totals(rows.size(),
                sum(rows, OrderReportRow::totalPaidUzs), sum(rows, OrderReportRow::goodsCostUzs),
                sum(rows, OrderReportRow::shippingCostUzs), sum(rows, OrderReportRow::serviceFeeUzs),
                sum(rows, OrderReportRow::profitUzs)));
    }

    /** {@code null}: this year. */
    public MonthlyReportResponse monthly(Integer requestedYear) {
        int year = requestedYear == null ? today().getYear() : requestedYear;
        if (year < 2000 || year > 2100) {
            throw new BusinessException("REPORT_YEAR_INVALID", "error.report.yearInvalid");
        }
        ReportPeriod period = ReportPeriod.year(year, zone);
        long[] orders = new long[12];
        BigDecimal[] revenue = new BigDecimal[12];
        BigDecimal[] profit = new BigDecimal[12];
        Arrays.fill(revenue, BigDecimal.ZERO);
        Arrays.fill(profit, BigDecimal.ZERO);
        for (ReportQueries.OrderRow order : queries.orders(period.start(), period.end())) {
            int m = order.createdAt().atZone(zone).getMonthValue() - 1;
            OrderProfit money = profit(order);
            orders[m]++;
            revenue[m] = revenue[m].add(money.paidUzs());
            profit[m] = profit[m].add(money.profitUzs());
        }
        List<MonthlyReportResponse.Month> months = new ArrayList<>();
        long allOrders = 0;
        BigDecimal allRevenue = BigDecimal.ZERO;
        BigDecimal allProfit = BigDecimal.ZERO;
        for (int m = 0; m < 12; m++) {
            months.add(new MonthlyReportResponse.Month(m + 1, orders[m], revenue[m], profit[m]));
            allOrders += orders[m];
            allRevenue = allRevenue.add(revenue[m]);
            allProfit = allProfit.add(profit[m]);
        }
        return new MonthlyReportResponse(year, months, allRevenue, allProfit, allOrders);
    }

    private OrderReportRow row(ReportQueries.OrderRow order) {
        OrderProfit money = profit(order);
        return new OrderReportRow(order.number(), order.createdAt().atZone(zone).toLocalDateTime(), order.phone(),
                order.status(), money.paidUzs(), money.goodsCostUzs(), money.shippingCostUzs(), money.serviceFeeUzs(),
                money.profitUzs());
    }

    private static OrderProfit profit(ReportQueries.OrderRow order) {
        return OrderProfit.of(order.netPaidUzs(), order.goodsCostUzs(), order.serviceFeeUzs(), order.shippingChargedUzs(),
                order.shippingCostPerKg(), order.ourMarkupPerKg(), order.paid() && order.status() != OrderStatus.CANCELLED);
    }

    private ReportPeriod period(LocalDate from, LocalDate to) {
        try {
            return ReportPeriod.of(from, to, today(), zone);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("REPORT_PERIOD_INVALID", "error.report.periodInvalid", ReportPeriod.MAX_DAYS);
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    private static BigDecimal sum(List<OrderReportRow> rows, Function<OrderReportRow, BigDecimal> column) {
        return rows.stream().map(column).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
