package by.delivery.stats_service.grpc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import com.google.protobuf.Timestamp;

import by.delivery.stats.grpc.AdminOverviewResponse;
import by.delivery.stats.grpc.CourierRating;
import by.delivery.stats.grpc.CourierStatsResponse;
import by.delivery.stats_service.config.StatsProperties;
import by.delivery.stats_service.service.model.AdminOverview;
import by.delivery.stats_service.service.model.CourierRatingEntry;
import by.delivery.stats_service.service.model.CourierStats;
import by.delivery.stats_service.service.model.Period;

@Component
public class StatisticsProtoMapper {

    private final ZoneId zoneId;

    public StatisticsProtoMapper(StatsProperties properties) {
        this.zoneId = properties.zoneId();
    }

    public Period toPeriod(boolean hasPeriod, by.delivery.stats.grpc.Period period) {
        if (!hasPeriod) {
            return Period.unbounded();
        }
        return new Period(
                period.hasFrom() ? toLocalDateTime(period.getFrom()) : null,
                period.hasTo() ? toLocalDateTime(period.getTo()) : null);
    }

    public CourierStatsResponse toResponse(CourierStats stats) {
        CourierStatsResponse.Builder builder = CourierStatsResponse.newBuilder()
                .setCourierId(stats.courierId())
                .setDeliveredCount(stats.deliveredCount())
                .setRatedReviewsCount(stats.ratedReviewsCount())
                .setPendingReviewsCount(stats.pendingReviewsCount());
        if (stats.fullName() != null) {
            builder.setFullName(stats.fullName());
        }
        if (stats.averageRating() != null) {
            builder.setAverageRating(stats.averageRating());
        }
        if (stats.averageDeliveryMinutes() != null) {
            builder.setAverageDeliveryMinutes(stats.averageDeliveryMinutes());
        }
        return builder.build();
    }

    public AdminOverviewResponse toResponse(AdminOverview overview) {
        AdminOverviewResponse.Builder builder = AdminOverviewResponse.newBuilder()
                .setDeliveredCount(overview.deliveredCount())
                .setCancelledCount(overview.cancelledCount())
                .setCancelledByClientCount(overview.cancelledByClientCount())
                .setCancelledByAdminCount(overview.cancelledByAdminCount())
                .setCancelledPercent(overview.cancelledPercent());
        if (overview.averageAssignmentMinutes() != null) {
            builder.setAverageAssignmentMinutes(overview.averageAssignmentMinutes());
        }
        if (overview.averageDeliveryMinutes() != null) {
            builder.setAverageDeliveryMinutes(overview.averageDeliveryMinutes());
        }
        for (CourierRatingEntry entry : overview.courierRatings()) {
            builder.addCourierRatings(toProto(entry));
        }
        return builder.build();
    }

    private CourierRating toProto(CourierRatingEntry entry) {
        CourierRating.Builder builder = CourierRating.newBuilder()
                .setPosition(entry.position())
                .setCourierId(entry.courierId())
                .setRatedReviewsCount(entry.ratedReviewsCount())
                .setDeliveredCount(entry.deliveredCount());
        if (entry.fullName() != null) {
            builder.setFullName(entry.fullName());
        }
        if (entry.averageRating() != null) {
            builder.setAverageRating(entry.averageRating());
        }
        return builder.build();
    }

    public LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return LocalDateTime.ofInstant(toInstant(timestamp), zoneId);
    }

    public Instant toInstant(Timestamp timestamp) {
        long seconds = timestamp.getSeconds();
        int nanos = timestamp.getNanos();
        // The allowed Protobuf Timestamp range is UTC years 0001 through 9999.
        if (seconds < -62_135_596_800L || seconds > 253_402_300_799L || nanos < 0 || nanos > 999_999_999) {
            throw new IllegalArgumentException("Некорректная метка времени");
        }
        return Instant.ofEpochSecond(seconds, nanos);
    }
}
