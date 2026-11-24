package by.delivery.stats_service.service.model;

public record CourierRatingEntry(
        int position,
        int courierId,
        String fullName,
        Double averageRating,
        long ratedReviewsCount,
        long deliveredCount) {
}
