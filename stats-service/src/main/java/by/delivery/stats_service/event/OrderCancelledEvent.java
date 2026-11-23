package by.delivery.stats_service.event;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import by.delivery.stats_service.repository.entity.CancelledBy;


@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCancelledEvent(
        UUID eventId,
        String type,
        LocalDateTime occurredAt,
        Integer orderId,
        Integer courierId,
        CancelledBy cancelledBy,
        LocalDateTime createdAt,
        LocalDateTime assignedAt,
        LocalDateTime pickedUpAt,
        LocalDateTime cancelledAt) {
}
