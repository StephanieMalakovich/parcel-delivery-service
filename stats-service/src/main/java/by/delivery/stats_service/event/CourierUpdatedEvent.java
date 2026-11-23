package by.delivery.stats_service.event;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)
public record CourierUpdatedEvent(
        UUID eventId,
        String type,
        LocalDateTime occurredAt,
        Integer courierId,
        String fullName) {
}
