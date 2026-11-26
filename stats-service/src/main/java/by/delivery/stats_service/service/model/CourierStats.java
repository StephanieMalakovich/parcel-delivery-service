package by.delivery.stats_service.service.model;

public record CourierStats(
        int courierId,
        String fullName,
        long deliveredCount,
        Double averageRating,
        long ratedReviewsCount,
        long pendingReviewsCount,
        Double averageDeliveryMinutes) {
}
