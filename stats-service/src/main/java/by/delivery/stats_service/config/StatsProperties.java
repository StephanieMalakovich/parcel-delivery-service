package by.delivery.stats_service.config;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;


@ConfigurationProperties(prefix = "stats")
public record StatsProperties(
        @DefaultValue Kafka kafka,
        @DefaultValue("7d") Duration ratingMaturity,
        String timeZone) {

    public record Kafka(
            @DefaultValue("delivery.analytics-events") String topic,
            @DefaultValue("3") int partitions) {
    }

    public ZoneId zoneId() {
        return timeZone == null || timeZone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(timeZone);
    }
}
