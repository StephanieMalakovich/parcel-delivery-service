package by.delivery.stats_service.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import by.delivery.stats_service.service.model.Period;
import lombok.RequiredArgsConstructor;


@Repository
@RequiredArgsConstructor
public class StatisticsQueryRepository {

    private static final String COMPLETED_AT =
            "CASE WHEN status = 'DELIVERED' THEN delivered_at ELSE cancelled_at END";

    private final JdbcClient jdbcClient;

    public record CourierDeliveries(long deliveredCount, Double averageDeliveryMinutes) {
    }

    public record CourierReviews(long ratedCount, Double averageRating, long pendingCount) {
    }

    public record OrderTotals(
            long deliveredCount,
            long cancelledCount,
            long cancelledByClientCount,
            long cancelledByAdminCount,
            Double averageAssignmentMinutes,
            Double averageDeliveryMinutes) {
    }

    public record CourierRatingRow(
            int courierId,
            String fullName,
            Double averageRating,
            long ratedCount,
            long deliveredCount) {
    }

    public CourierDeliveries courierDeliveries(int courierId, Period period) {
        return jdbcClient.sql("""
                        SELECT COUNT(*) AS delivered_count,
                               AVG(EXTRACT(EPOCH FROM (delivered_at - picked_up_at)) / 60.0) AS avg_delivery_minutes
                        FROM order_facts
                        WHERE courier_id = :courierId
                          AND status = 'DELIVERED'
                          AND delivered_at >= :from AND delivered_at < :to
                        """)
                .param("courierId", courierId)
                .param("from", period.fromOrMin())
                .param("to", period.toOrMax())
                .query((rs, rowNum) -> new CourierDeliveries(
                        rs.getLong("delivered_count"),
                        nullableDouble(rs, "avg_delivery_minutes")))
                .single();
    }


    public CourierReviews courierReviews(int courierId, LocalDateTime maturityCutoff) {
        return jdbcClient.sql("""
                        SELECT COUNT(*) FILTER (WHERE created_at <= :cutoff) AS rated_count,
                               AVG(rating) FILTER (WHERE created_at <= :cutoff) AS avg_rating,
                               COUNT(*) FILTER (WHERE created_at > :cutoff) AS pending_count
                        FROM review_facts
                        WHERE courier_id = :courierId
                        """)
                .param("courierId", courierId)
                .param("cutoff", maturityCutoff)
                .query((rs, rowNum) -> new CourierReviews(
                        rs.getLong("rated_count"),
                        nullableDouble(rs, "avg_rating"),
                        rs.getLong("pending_count")))
                .single();
    }

    public OrderTotals orderTotals(Period period) {
        return jdbcClient.sql("""
                        SELECT COUNT(*) FILTER (WHERE status = 'DELIVERED') AS delivered_count,
                               COUNT(*) FILTER (WHERE status = 'CANCELLED') AS cancelled_count,
                               COUNT(*) FILTER (WHERE status = 'CANCELLED' AND cancelled_by = 'CLIENT') AS by_client_count,
                               COUNT(*) FILTER (WHERE status = 'CANCELLED' AND cancelled_by = 'ADMIN') AS by_admin_count,
                               AVG(EXTRACT(EPOCH FROM (assigned_at - created_at)) / 60.0) AS avg_assignment_minutes,
                               AVG(EXTRACT(EPOCH FROM (delivered_at - picked_up_at)) / 60.0)
                                   FILTER (WHERE status = 'DELIVERED') AS avg_delivery_minutes
                        FROM order_facts
                        WHERE %1$s >= :from AND %1$s < :to
                        """.formatted(COMPLETED_AT))
                .param("from", period.fromOrMin())
                .param("to", period.toOrMax())
                .query((rs, rowNum) -> new OrderTotals(
                        rs.getLong("delivered_count"),
                        rs.getLong("cancelled_count"),
                        rs.getLong("by_client_count"),
                        rs.getLong("by_admin_count"),
                        nullableDouble(rs, "avg_assignment_minutes"),
                        nullableDouble(rs, "avg_delivery_minutes")))
                .single();
    }


    public List<CourierRatingRow> courierRatings(Period period, LocalDateTime maturityCutoff) {
        return jdbcClient.sql("""
                        WITH known_couriers AS (
                            SELECT courier_id FROM courier_facts
                            UNION
                            SELECT courier_id FROM review_facts
                            UNION
                            SELECT courier_id FROM order_facts WHERE courier_id IS NOT NULL
                        ),
                        ratings AS (
                            SELECT courier_id, AVG(rating) AS avg_rating, COUNT(*) AS rated_count
                            FROM review_facts
                            WHERE created_at <= :cutoff
                            GROUP BY courier_id
                        ),
                        deliveries AS (
                            SELECT courier_id, COUNT(*) AS delivered_count
                            FROM order_facts
                            WHERE status = 'DELIVERED' AND delivered_at >= :from AND delivered_at < :to
                            GROUP BY courier_id
                        )
                        SELECT k.courier_id,
                               cf.full_name,
                               r.avg_rating,
                               COALESCE(r.rated_count, 0) AS rated_count,
                               COALESCE(d.delivered_count, 0) AS delivered_count
                        FROM known_couriers k
                        LEFT JOIN courier_facts cf ON cf.courier_id = k.courier_id
                        LEFT JOIN ratings r ON r.courier_id = k.courier_id
                        LEFT JOIN deliveries d ON d.courier_id = k.courier_id
                        ORDER BY r.avg_rating DESC NULLS LAST,
                                 rated_count DESC,
                                 delivered_count DESC,
                                 k.courier_id
                        """)
                .param("cutoff", maturityCutoff)
                .param("from", period.fromOrMin())
                .param("to", period.toOrMax())
                .query((rs, rowNum) -> new CourierRatingRow(
                        rs.getInt("courier_id"),
                        rs.getString("full_name"),
                        nullableDouble(rs, "avg_rating"),
                        rs.getLong("rated_count"),
                        rs.getLong("delivered_count")))
                .list();
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
