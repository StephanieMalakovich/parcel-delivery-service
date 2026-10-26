package by.delivery.main_service.dto;

import java.time.LocalDateTime;

import by.delivery.main_service.repository.entity.Review;

public record ReviewResponse(
        Integer id,
        Integer orderId,
        Integer courierId,
        Integer rating,
        String comment,
        LocalDateTime createdAt) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getOrder().getId(),
                review.getCourier().getId(),
                review.getRating().intValue(),
                review.getComment(),
                review.getCreatedAt());
    }
}
