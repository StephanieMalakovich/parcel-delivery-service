package by.delivery.stats_service.service.model;

import java.util.List;

public record AdminOverview(
        long deliveredCount,
        long cancelledCount,
        long cancelledByClientCount,
        long cancelledByAdminCount,
        double cancelledPercent,
        Double averageAssignmentMinutes,
        Double averageDeliveryMinutes,
        List<CourierRatingEntry> courierRatings) {
}
