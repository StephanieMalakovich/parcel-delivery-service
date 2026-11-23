package by.delivery.stats_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.delivery.stats_service.config.StatsProperties;
import by.delivery.stats_service.repository.CourierFactRepository;
import by.delivery.stats_service.repository.StatisticsQueryRepository;
import by.delivery.stats_service.repository.StatisticsQueryRepository.CourierDeliveries;
import by.delivery.stats_service.repository.StatisticsQueryRepository.CourierRatingRow;
import by.delivery.stats_service.repository.StatisticsQueryRepository.CourierReviews;
import by.delivery.stats_service.repository.StatisticsQueryRepository.OrderTotals;
import by.delivery.stats_service.repository.entity.CourierFact;
import by.delivery.stats_service.service.model.AdminOverview;
import by.delivery.stats_service.service.model.CourierRatingEntry;
import by.delivery.stats_service.service.model.CourierStats;
import by.delivery.stats_service.service.model.Period;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

    private final StatisticsQueryRepository queries;
    private final CourierFactRepository courierFactRepository;
    private final StatsProperties properties;
    private final Clock clock;

    public CourierStats getCourierStats(int courierId, Period period) {
        if (courierId <= 0) {
            throw new IllegalArgumentException("courier_id должен быть положительным, получено: " + courierId);
        }
        CourierDeliveries deliveries = queries.courierDeliveries(courierId, period);
        CourierReviews reviews = queries.courierReviews(courierId, maturityCutoff());
        String fullName = courierFactRepository.findById(courierId)
                .map(CourierFact::getFullName)
                .orElse(null);

        return new CourierStats(
                courierId,
                fullName,
                deliveries.deliveredCount(),
                round(reviews.averageRating()),
                reviews.ratedCount(),
                reviews.pendingCount(),
                round(deliveries.averageDeliveryMinutes()));
    }

    public AdminOverview getAdminOverview(Period period) {
        OrderTotals totals = queries.orderTotals(period);
        long finished = totals.deliveredCount() + totals.cancelledCount();
        double cancelledPercent = finished == 0 ? 0.0 : 100.0 * totals.cancelledCount() / finished;

        List<CourierRatingRow> rows = queries.courierRatings(period, maturityCutoff());
        List<CourierRatingEntry> ratings = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            CourierRatingRow row = rows.get(i);
            ratings.add(new CourierRatingEntry(
                    i + 1,
                    row.courierId(),
                    row.fullName(),
                    round(row.averageRating()),
                    row.ratedCount(),
                    row.deliveredCount()));
        }

        return new AdminOverview(
                totals.deliveredCount(),
                totals.cancelledCount(),
                totals.cancelledByClientCount(),
                totals.cancelledByAdminCount(),
                round(cancelledPercent),
                round(totals.averageAssignmentMinutes()),
                round(totals.averageDeliveryMinutes()),
                ratings);
    }

    private LocalDateTime maturityCutoff() {
        return LocalDateTime.now(clock).minus(properties.ratingMaturity());
    }

    private static Double round(Double value) {
        return value == null ? null : round(value.doubleValue());
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
