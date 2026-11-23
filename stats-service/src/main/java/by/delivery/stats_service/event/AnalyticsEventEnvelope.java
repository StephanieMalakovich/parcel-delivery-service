package by.delivery.stats_service.event;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalyticsEventEnvelope(UUID eventId, String type) {
}
