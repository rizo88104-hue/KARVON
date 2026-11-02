package uz.karvon.order.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import uz.karvon.order.domain.OrderStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orders that have been in their current status since before a moment (from the status history).
 */
@Repository
@RequiredArgsConstructor
public class DelayedOrderQueries {

    private final JdbcTemplate jdbc;

    public record Row(UUID id, String number, OrderStatus status, UUID buyerId, Instant since) {
    }

    /** In {@code status} since before {@code cutoff}, longest first. */
    public List<Row> inStatusSince(OrderStatus status, Instant cutoff) {
        return jdbc.query("""
                        SELECT o.id, o.number, o.status, o.buyer_id, h.since
                        FROM orders o
                        JOIN LATERAL (SELECT max(created_at) AS since FROM order_status_history
                                      WHERE order_id = o.id AND to_status = o.status) h ON true
                        WHERE o.status = ? AND h.since < ?
                        ORDER BY h.since""",
                (rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("number"),
                        OrderStatus.valueOf(rs.getString("status")), rs.getObject("buyer_id", UUID.class),
                        rs.getTimestamp("since").toInstant()),
                status.name(), Timestamp.from(cutoff));
    }
}
