package by.delivery.main_service.event;

import java.time.LocalDateTime;

public record NewReviewEvent(
        Integer reviewId,
        Integer orderId,
        Integer courierId,
        Integer rating,
        String comment,
        LocalDateTime createdAt) {
}
