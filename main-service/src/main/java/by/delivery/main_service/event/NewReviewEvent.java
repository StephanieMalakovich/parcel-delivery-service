package by.delivery.main_service.event;

import java.time.LocalDateTime;
import java.util.UUID;

import by.delivery.main_service.repository.entity.Review;

public record NewReviewEvent(
        UUID eventId,
        String type,
        LocalDateTime occurredAt,
        Integer reviewId,
        Integer orderId,
        Integer courierId,
        Integer rating,
        String comment,
        LocalDateTime createdAt) {

    public static final String TYPE = "NEW_REVIEW";

    public static NewReviewEvent from(Review review) {
        return new NewReviewEvent(
                UUID.randomUUID(),
                TYPE,
                LocalDateTime.now(),
                review.getId(),
                review.getOrder().getId(),
                review.getCourier().getId(),
                review.getRating().intValue(),
                review.getComment(),
                review.getCreatedAt());
    }
}