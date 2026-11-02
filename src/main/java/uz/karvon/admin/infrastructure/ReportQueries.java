package uz.karvon.admin.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import uz.karvon.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only report queries with plain projections (no entities are loaded). This is the reporting read model of the
 * admin module: it only reads the orders, order items, users and pricing settings tables and never writes them.
 */
@Repository
@RequiredArgsConstructor
public class ReportQueries {

    /** Order statuses where everything not out of stock has been bought on Taobao. */
    private static final String BOUGHT_STATUSES =
            "'PURCHASED', 'AT_WAREHOUSE', 'AWAITING_SHIPPING_PAYMENT', 'IN_TRANSIT', 'ARRIVED', 'DELIVERED'";

    /** Paid, not cancelled: the order brings the service fee and the shipping markup. */
    private static final String EARNING = "o.paid_at IS NOT NULL AND o.status <> 'CANCELLED'";

    private final JdbcTemplate jdbc;

    public record Totals(long orders, long paidOrders, BigDecimal revenue, BigDecimal serviceIncome,
                         BigDecimal shippingIncome) {
    }

    public record Day(LocalDate date, long orders, BigDecimal revenue) {
    }

    /**
     * One order with what the profit needs. {@code goodsCostUzs} is not rounded: the sum over bought items of
     * (actual price, or the order price when not entered, + China shipping) * quantity * the customer rate that was
     * active when the item was bought (the order's own rate when no settings row is that old).
     */
    public record OrderRow(String number, Instant createdAt, String phone, OrderStatus status, boolean paid,
                           BigDecimal netPaidUzs, BigDecimal serviceFeeUzs, BigDecimal shippingChargedUzs,
                           BigDecimal shippingCostPerKg, BigDecimal ourMarkupPerKg, BigDecimal goodsCostUzs) {
    }

    public long ordersCreated(Instant from, Instant to) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM orders WHERE created_at >= ? AND created_at < ?",
                Long.class, Timestamp.from(from), Timestamp.from(to));
        return count == null ? 0 : count;
    }

    /** The shipping markup is rounded to 100 so'm per order, as in OrderProfit.shippingMarkup. */
    public Totals totals(Instant from, Instant to) {
        return jdbc.queryForObject("""
                        SELECT count(*) AS orders,
                               count(*) FILTER (WHERE o.paid_at IS NOT NULL) AS paid_orders,
                               COALESCE(sum(o.paid_uzs - o.refunded_uzs), 0) AS revenue,
                               COALESCE(sum(o.service_fee_uzs) FILTER (WHERE %1$s), 0) AS service_income,
                               COALESCE(sum(round(COALESCE(o.final_shipping_uzs, o.prepaid_shipping_uzs) * s.our_markup_per_kg
                                                  / NULLIF(s.shipping_cost_per_kg + s.our_markup_per_kg, 0), -2))
                                        FILTER (WHERE %1$s), 0) AS shipping_income
                        FROM orders o
                        JOIN pricing_settings s ON s.id = o.pricing_settings_id
                        WHERE o.created_at >= ? AND o.created_at < ?""".formatted(EARNING),
                (rs, n) -> new Totals(rs.getLong("orders"), rs.getLong("paid_orders"), rs.getBigDecimal("revenue"),
                        rs.getBigDecimal("service_income"), rs.getBigDecimal("shipping_income")),
                Timestamp.from(from), Timestamp.from(to));
    }

    /** Every status, 0 for those without orders. */
    public Map<OrderStatus, Long> countByStatus(Instant from, Instant to) {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status, 0L);
        }
        jdbc.query("SELECT status, count(*) AS n FROM orders WHERE created_at >= ? AND created_at < ? GROUP BY status",
                rs -> {
                    counts.put(OrderStatus.valueOf(rs.getString("status")), rs.getLong("n"));
                }, Timestamp.from(from), Timestamp.from(to));
        return counts;
    }

    /** Only days that have orders, by day in {@code zone}. */
    public List<Day> daily(Instant from, Instant to, ZoneId zone) {
        return jdbc.query("""
                        SELECT (created_at AT TIME ZONE ?)::date AS day, count(*) AS orders,
                               COALESCE(sum(paid_uzs - refunded_uzs), 0) AS revenue
                        FROM orders
                        WHERE created_at >= ? AND created_at < ?
                        GROUP BY 1
                        ORDER BY 1""",
                (rs, n) -> new Day(rs.getObject("day", LocalDate.class), rs.getLong("orders"), rs.getBigDecimal("revenue")),
                zone.getId(), Timestamp.from(from), Timestamp.from(to));
    }

    /** Oldest first. */
    public List<OrderRow> orders(Instant from, Instant to) {
        return jdbc.query("""
                        SELECT o.number, o.created_at, u.phone, o.status, o.paid_at IS NOT NULL AS paid,
                               o.paid_uzs - o.refunded_uzs AS net_paid,
                               o.service_fee_uzs,
                               COALESCE(o.final_shipping_uzs, o.prepaid_shipping_uzs) AS shipping_charged,
                               s.shipping_cost_per_kg, s.our_markup_per_kg,
                               COALESCE(g.goods_cost, 0) AS goods_cost
                        FROM orders o
                        JOIN users u ON u.id = o.customer_id
                        JOIN pricing_settings s ON s.id = o.pricing_settings_id
                        LEFT JOIN LATERAL (
                            SELECT sum((COALESCE(i.actual_price_cny, i.price_cny) + i.china_shipping_cny) * i.quantity
                                       * COALESCE(r.customer_rate, s.customer_rate)) AS goods_cost
                            FROM order_items i
                            LEFT JOIN LATERAL (
                                SELECT ps.customer_rate FROM pricing_settings ps
                                WHERE ps.effective_from <= COALESCE(i.purchased_at, o.paid_at, o.created_at)
                                ORDER BY ps.effective_from DESC
                                LIMIT 1) r ON true
                            WHERE i.order_id = o.id
                              AND (i.status = 'PURCHASED' OR (i.status <> 'OUT_OF_STOCK' AND o.status IN (%s)))
                        ) g ON true
                        WHERE o.created_at >= ? AND o.created_at < ?
                        ORDER BY o.created_at, o.number""".formatted(BOUGHT_STATUSES),
                (rs, n) -> new OrderRow(rs.getString("number"), rs.getTimestamp("created_at").toInstant(),
                        rs.getString("phone"), OrderStatus.valueOf(rs.getString("status")), rs.getBoolean("paid"),
                        rs.getBigDecimal("net_paid"), rs.getBigDecimal("service_fee_uzs"),
                        rs.getBigDecimal("shipping_charged"), rs.getBigDecimal("shipping_cost_per_kg"),
                        rs.getBigDecimal("our_markup_per_kg"), rs.getBigDecimal("goods_cost")),
                Timestamp.from(from), Timestamp.from(to));
    }
}
