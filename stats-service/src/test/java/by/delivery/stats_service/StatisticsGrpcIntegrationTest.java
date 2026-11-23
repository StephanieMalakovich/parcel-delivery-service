package by.delivery.stats_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import com.google.protobuf.Timestamp;

import by.delivery.stats.grpc.AdminOverviewRequest;
import by.delivery.stats.grpc.AdminOverviewResponse;
import by.delivery.stats.grpc.CourierRating;
import by.delivery.stats.grpc.CourierStatsRequest;
import by.delivery.stats.grpc.CourierStatsResponse;
import by.delivery.stats.grpc.Period;
import by.delivery.stats.grpc.StatisticsServiceGrpc;
import by.delivery.stats_service.service.EventProcessingService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

@SpringBootTest(properties = {
        "stats.time-zone=UTC",
        "stats.kafka.topic=analytics-events-test",
        "spring.kafka.consumer.group-id=stats-service-test"
})
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = StatisticsServiceGrpc.StatisticsServiceBlockingStub.class)
@Import({TestcontainersConfiguration.class, StatisticsGrpcIntegrationTest.FixedClockConfig.class})
class StatisticsGrpcIntegrationTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        }
    }

    @Autowired
    private StatisticsServiceGrpc.StatisticsServiceBlockingStub stub;

    @Autowired
    private EventProcessingService eventProcessingService;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE order_facts, review_facts, courier_facts, processed_events");
    }

    @Test
    void courierStatsCountDeliveriesRatingAndDeliveryTime() {
        handle(courierUpdated(1, "Иван Петров"));
        // доставки: 30 и 50 минут от «забрал» до «доставил»
        handle(delivered(101, 1, NOW.minusDays(3), 30));
        handle(delivered(102, 1, NOW.minusDays(2), 50));
        // отмена не считается доставкой
        handle(cancelled(103, 1, "CLIENT", NOW.minusDays(1)));
        // старый отзыв учитывается в рейтинге, свежий — ещё нет
        handle(review(201, 101, 1, 5, NOW.minusDays(10)));
        handle(review(202, 102, 1, 1, NOW.minusDays(1)));

        CourierStatsResponse stats = stub.getCourierStats(CourierStatsRequest.newBuilder()
                .setCourierId(1)
                .build());

        assertThat(stats.getCourierId()).isEqualTo(1);
        assertThat(stats.getFullName()).isEqualTo("Иван Петров");
        assertThat(stats.getDeliveredCount()).isEqualTo(2);
        assertThat(stats.hasAverageRating()).isTrue();
        assertThat(stats.getAverageRating()).isEqualTo(5.0);
        assertThat(stats.getRatedReviewsCount()).isEqualTo(1);
        assertThat(stats.getPendingReviewsCount()).isEqualTo(1);
        assertThat(stats.getAverageDeliveryMinutes()).isEqualTo(40.0);
    }

    @Test
    void courierStatsRespectPeriod() {
        handle(delivered(101, 1, NOW.minusDays(10), 30));
        handle(delivered(102, 1, NOW.minusDays(2), 60));

        CourierStatsResponse stats = stub.getCourierStats(CourierStatsRequest.newBuilder()
                .setCourierId(1)
                .setPeriod(Period.newBuilder().setFrom(timestamp(NOW.minusDays(5))).setTo(timestamp(NOW)))
                .build());

        assertThat(stats.getDeliveredCount()).isEqualTo(1);
        assertThat(stats.getAverageDeliveryMinutes()).isEqualTo(60.0);
    }

    @Test
    void courierWithoutDataGetsEmptyStats() {
        CourierStatsResponse stats = stub.getCourierStats(CourierStatsRequest.newBuilder()
                .setCourierId(42)
                .build());

        assertThat(stats.getDeliveredCount()).isZero();
        assertThat(stats.hasAverageRating()).isFalse();
        assertThat(stats.hasAverageDeliveryMinutes()).isFalse();
        assertThat(stats.getFullName()).isEmpty();
    }

    @Test
    void adminOverviewCountsCancellationsAndBuildsRating() {
        handle(courierUpdated(1, "Иван Петров"));
        handle(courierUpdated(2, "Анна Смирнова"));
        handle(courierUpdated(3, "Новый Курьер"));
        handle(delivered(101, 1, NOW.minusDays(3), 20));
        handle(delivered(102, 2, NOW.minusDays(3), 40));
        handle(delivered(103, 2, NOW.minusDays(2), 60));
        handle(cancelled(104, null, "CLIENT", NOW.minusDays(1)));
        handle(review(201, 101, 1, 3, NOW.minusDays(9)));
        handle(review(202, 102, 2, 5, NOW.minusDays(8)));
        handle(review(203, 103, 2, 4, NOW.minusDays(8)));

        AdminOverviewResponse overview = stub.getAdminOverview(AdminOverviewRequest.getDefaultInstance());

        assertThat(overview.getDeliveredCount()).isEqualTo(3);
        assertThat(overview.getCancelledCount()).isEqualTo(1);
        assertThat(overview.getCancelledByClientCount()).isEqualTo(1);
        assertThat(overview.getCancelledByAdminCount()).isZero();
        assertThat(overview.getCancelledPercent()).isEqualTo(25.0);
        // назначение в тестовых данных всегда через 10 минут после создания
        assertThat(overview.getAverageAssignmentMinutes()).isEqualTo(10.0);
        assertThat(overview.getAverageDeliveryMinutes()).isEqualTo(40.0);

        assertThat(overview.getCourierRatingsList())
                .extracting(CourierRating::getPosition, CourierRating::getCourierId, CourierRating::getDeliveredCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, 2, 2L),
                        org.assertj.core.groups.Tuple.tuple(2, 1, 1L),
                        org.assertj.core.groups.Tuple.tuple(3, 3, 0L));
        CourierRating best = overview.getCourierRatings(0);
        assertThat(best.getFullName()).isEqualTo("Анна Смирнова");
        assertThat(best.getAverageRating()).isEqualTo(4.5);
        assertThat(overview.getCourierRatings(2).hasAverageRating()).isFalse();
    }

    @Test
    void repeatedEventIsProcessedOnce() {
        String event = review(201, 101, 1, 5, NOW.minusDays(10));
        handle(event);
        handle(event);

        Integer reviews = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM review_facts", Integer.class);
        Integer processed = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processed_events", Integer.class);
        assertThat(reviews).isEqualTo(1);
        assertThat(processed).isEqualTo(1);
    }

    @Test
    void laterCancellationReplacesDelivery() {
        handle(delivered(101, 1, NOW.minusDays(2), 30));
        handle(cancelled(101, 1, "ADMIN", NOW.minusDays(1)));

        AdminOverviewResponse overview = stub.getAdminOverview(AdminOverviewRequest.getDefaultInstance());

        assertThat(overview.getDeliveredCount()).isZero();
        assertThat(overview.getCancelledByAdminCount()).isEqualTo(1);
    }

    @Test
    void invalidRequestsAreRejected() {
        assertThatThrownBy(() -> stub.getCourierStats(CourierStatsRequest.newBuilder().setCourierId(0).build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);

        Period reversed = Period.newBuilder().setFrom(timestamp(NOW)).setTo(timestamp(NOW.minusDays(1))).build();
        assertThatThrownBy(() -> stub.getAdminOverview(AdminOverviewRequest.newBuilder().setPeriod(reversed).build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void eventFromKafkaReachesStatistics() throws Exception {
        kafkaTemplate.send("analytics-events-test", "501", delivered(501, 7, NOW.minusDays(1), 25)).get();

        CourierStatsResponse stats = null;
        for (int attempt = 0; attempt < 100; attempt++) {
            stats = stub.getCourierStats(CourierStatsRequest.newBuilder().setCourierId(7).build());
            if (stats.getDeliveredCount() == 1) {
                break;
            }
            Thread.sleep(200);
        }
        assertThat(stats.getDeliveredCount()).isEqualTo(1);
        assertThat(stats.getAverageDeliveryMinutes()).isEqualTo(25.0);
    }


    private void handle(String json) {
        eventProcessingService.handle(json);
    }

    private static String courierUpdated(int courierId, String fullName) {
        return """
                {"eventId":"%s","type":"COURIER_UPDATED","occurredAt":"%s","courierId":%d,"fullName":"%s"}
                """.formatted(UUID.randomUUID(), NOW, courierId, fullName);
    }

    private static String delivered(int orderId, int courierId, LocalDateTime deliveredAt, int minutes) {
        LocalDateTime pickedUpAt = deliveredAt.minusMinutes(minutes);
        LocalDateTime createdAt = pickedUpAt.minusMinutes(30);
        return """
                {"eventId":"%s","type":"ORDER_DELIVERED","occurredAt":"%s","orderId":%d,"courierId":%d,
                 "createdAt":"%s","assignedAt":"%s","pickedUpAt":"%s","deliveredAt":"%s"}
                """.formatted(UUID.randomUUID(), deliveredAt, orderId, courierId,
                createdAt, createdAt.plusMinutes(10), pickedUpAt, deliveredAt);
    }

    private static String cancelled(int orderId, Integer courierId, String cancelledBy, LocalDateTime cancelledAt) {
        LocalDateTime createdAt = cancelledAt.minusHours(1);
        String assignedAt = courierId == null ? "null" : "\"" + createdAt.plusMinutes(10) + "\"";
        return """
                {"eventId":"%s","type":"ORDER_CANCELLED","occurredAt":"%s","orderId":%d,"courierId":%s,
                 "status":"CANCELLED","cancelledBy":"%s","createdAt":"%s","assignedAt":%s,"pickedUpAt":null,
                 "cancelledAt":"%s"}
                """.formatted(UUID.randomUUID(), cancelledAt, orderId, courierId, cancelledBy,
                createdAt, assignedAt, cancelledAt);
    }

    private static String review(int reviewId, int orderId, int courierId, int rating, LocalDateTime createdAt) {
        return """
                {"eventId":"%s","type":"NEW_REVIEW","occurredAt":"%s","reviewId":%d,"orderId":%d,
                 "courierId":%d,"rating":%d,"comment":"ok","createdAt":"%s"}
                """.formatted(UUID.randomUUID(), createdAt, reviewId, orderId, courierId, rating, createdAt);
    }

    private static Timestamp timestamp(LocalDateTime value) {
        Instant instant = value.toInstant(ZoneOffset.UTC);
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }
}
