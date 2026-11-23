package by.delivery.stats_service.event;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)
public record NewReviewEvent(
        UUID eventId,
        String type,
        LocalDateTime occurredAt,
        Integer reviewId,
        Integer orderId,
        Integer courierId,
        Integer rating,
        LocalDateTime createdAt) {
}
