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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.jdbc.core.JdbcTemplate;

import com.google.protobuf.Timestamp;

import by.delivery.stats.grpc.AdminOverviewRequest;
import by.delivery.stats.grpc.AdminOverviewResponse;
import by.delivery.stats.grpc.CourierRating;
import by.delivery.stats.grpc.CourierStatsRequest;
import by.delivery.stats.grpc.CourierStatsResponse;
import by.delivery.stats.grpc.CourierUpdated;
import by.delivery.stats.grpc.OrderCancelled;
import by.delivery.stats.grpc.OrderDelivered;
import by.delivery.stats.grpc.Period;
import by.delivery.stats.grpc.RecordAnalyticsEventRequest;
import by.delivery.stats.grpc.ReviewCreated;
import by.delivery.stats.grpc.StatisticsServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

@SpringBootTest(properties = "stats.time-zone=UTC")
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = StatisticsServiceGrpc.StatisticsServiceBlockingStub.class)
@Import({ TestcontainersConfiguration.class, StatisticsGrpcIntegrationTest.FixedClockConfig.class })
class StatisticsGrpcIntegrationTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);
    private static final long SEVEN_DAYS_IN_SECONDS = 7 * 24 * 60 * 60;

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
        handle(cancelled(103, 1, OrderCancelled.Initiator.CLIENT, NOW.minusDays(1)));
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

    @ParameterizedTest
    @ValueSource(longs = { 0, SEVEN_DAYS_IN_SECONDS - 1, SEVEN_DAYS_IN_SECONDS })
    void adminIncludesReviewImmediatelyWhileCourierWaitsSevenDays(long reviewAgeSeconds) {
        handle(review(201, 101, 1, 5, NOW.minusSeconds(reviewAgeSeconds)));

        AdminOverviewResponse overview = stub.getAdminOverview(AdminOverviewRequest.getDefaultInstance());
        assertThat(overview.getCourierRatingsList()).singleElement().satisfies(rating -> {
            assertThat(rating.getCourierId()).isEqualTo(1);
            assertThat(rating.getRatedReviewsCount()).isEqualTo(1);
            assertThat(rating.hasAverageRating()).isTrue();
            assertThat(rating.getAverageRating()).isEqualTo(5.0);
        });

        CourierStatsResponse stats = stub.getCourierStats(CourierStatsRequest.newBuilder().setCourierId(1).build());
        if (reviewAgeSeconds == SEVEN_DAYS_IN_SECONDS) {
            assertThat(stats.getRatedReviewsCount()).isEqualTo(1);
            assertThat(stats.getPendingReviewsCount()).isZero();
            assertThat(stats.hasAverageRating()).isTrue();
            assertThat(stats.getAverageRating()).isEqualTo(5.0);
        } else {
            assertThat(stats.getRatedReviewsCount()).isZero();
            assertThat(stats.getPendingReviewsCount()).isEqualTo(1);
            assertThat(stats.hasAverageRating()).isFalse();
        }
    }

    @Test
    void adminOverviewCountsCancellationsAndBuildsRating() {
        handle(courierUpdated(1, "Иван Петров"));
        handle(courierUpdated(2, "Анна Смирнова"));
        handle(courierUpdated(3, "Новый Курьер"));
        handle(delivered(101, 1, NOW.minusDays(3), 20));
        handle(delivered(102, 2, NOW.minusDays(3), 40));
        handle(delivered(103, 2, NOW.minusDays(2), 60));
        handle(cancelled(104, null, OrderCancelled.Initiator.CLIENT, NOW.minusDays(1)));
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
        var event = review(201, 101, 1, 5, NOW.minusDays(10));
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
        handle(cancelled(101, 1, OrderCancelled.Initiator.ADMIN, NOW.minusDays(1)));
        handle(delivered(101, 1, NOW.minusDays(2), 30));

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
    void invalidFactRollsBackTheDeduplicationMarker() {
        var valid = review(201, 101, 1, 5, NOW.minusDays(10));
        var invalid = valid.toBuilder().setReviewCreated(valid.getReviewCreated().toBuilder().setRating(9)).build();
        assertThatThrownBy(() -> handle(invalid))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
        handle(valid);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM review_facts", Integer.class)).isEqualTo(1);
    }

    @Test
    void olderCourierUpdateDoesNotOverwriteTheNewName() {
        handle(courierUpdated(7, "Новое имя"));
        var old = courierUpdated(7, "Старое имя");
        handle(old.toBuilder().setCourierUpdated(old.getCourierUpdated().toBuilder()
                .setUpdatedAt(timestamp(NOW.minusDays(1)))).build());
        assertThat(stub.getCourierStats(CourierStatsRequest.newBuilder().setCourierId(7).build()).getFullName())
                .isEqualTo("Новое имя");
    }

    private void handle(RecordAnalyticsEventRequest event) {
        stub.recordAnalyticsEvent(event);
    }

    private static RecordAnalyticsEventRequest courierUpdated(int courierId, String fullName) {
        return event().setCourierUpdated(CourierUpdated.newBuilder()
                .setCourierId(courierId).setFullName(fullName).setUpdatedAt(timestamp(NOW))).build();
    }

    private static RecordAnalyticsEventRequest delivered(int orderId, int courierId,
            LocalDateTime deliveredAt, int minutes) {
        LocalDateTime pickedUpAt = deliveredAt.minusMinutes(minutes);
        LocalDateTime createdAt = pickedUpAt.minusMinutes(30);
        return event().setOrderDelivered(OrderDelivered.newBuilder().setOrderId(orderId).setCourierId(courierId)
                .setCreatedAt(timestamp(createdAt)).setAssignedAt(timestamp(createdAt.plusMinutes(10)))
                .setPickedUpAt(timestamp(pickedUpAt)).setDeliveredAt(timestamp(deliveredAt))).build();
    }

    private static RecordAnalyticsEventRequest cancelled(int orderId, Integer courierId,
            OrderCancelled.Initiator cancelledBy,
            LocalDateTime cancelledAt) {
        LocalDateTime createdAt = cancelledAt.minusHours(1);
        var fact = OrderCancelled.newBuilder().setOrderId(orderId).setCancelledBy(cancelledBy)
                .setCreatedAt(timestamp(createdAt)).setCancelledAt(timestamp(cancelledAt));
        if (courierId != null) {
            fact.setCourierId(courierId).setAssignedAt(timestamp(createdAt.plusMinutes(10)));
        }
        return event().setOrderCancelled(fact).build();
    }

    private static RecordAnalyticsEventRequest review(int reviewId, int orderId, int courierId, int rating,
            LocalDateTime createdAt) {
        return event().setReviewCreated(ReviewCreated.newBuilder()
                .setReviewId(reviewId).setOrderId(orderId).setCourierId(courierId)
                .setRating(rating).setCreatedAt(timestamp(createdAt))).build();
    }

    private static RecordAnalyticsEventRequest.Builder event() {
        return RecordAnalyticsEventRequest.newBuilder().setEventId(UUID.randomUUID().toString());
    }

    private static Timestamp timestamp(LocalDateTime value) {
        Instant instant = value.toInstant(ZoneOffset.UTC);
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }
}
