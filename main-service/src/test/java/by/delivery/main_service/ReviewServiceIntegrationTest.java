package by.delivery.main_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import by.delivery.main_service.event.NewReviewEvent;
import by.delivery.main_service.repository.ClientRepository;
import by.delivery.main_service.repository.CourierRepository;
import by.delivery.main_service.repository.OrderRepository;
import by.delivery.main_service.repository.ReviewRepository;
import by.delivery.main_service.repository.ZoneRepository;
import by.delivery.main_service.repository.entity.Client;
import by.delivery.main_service.repository.entity.Courier;
import by.delivery.main_service.repository.entity.CourierStatus;
import by.delivery.main_service.repository.entity.Order;
import by.delivery.main_service.repository.entity.OrderStatus;
import by.delivery.main_service.repository.entity.Review;
import by.delivery.main_service.repository.entity.Zone;
import by.delivery.main_service.service.ReviewService;

import jakarta.persistence.EntityNotFoundException;

@SpringBootTest(properties = "spring.liquibase.contexts=test")
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class ReviewServiceIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    reviews,
                    parcels,
                    courier_zones,
                    notifications,
                    orders,
                    clients,
                    couriers,
                    zones
                RESTART IDENTITY CASCADE
                """);
    }

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private CourierRepository courierRepository;

    @Autowired
    private ZoneRepository zoneRepository;


    @Test
    void givenDeliveredOrder_whenClientLeavesReview_thenReviewIsSaved(ApplicationEvents applicationEvents) {

        // Given
        Order order = createOrder(OrderStatus.DELIVERED);

        Integer orderId = order.getId();

        // When
        reviewService.leaveReview(
                orderId,
                "1001",
                5,
                "Excellent delivery");

        // Then
        Review savedReview = reviewRepository
                .findByOrder_Id(orderId)
                .orElseThrow();

        assertThat(savedReview.getRating()).isEqualTo((short) 5);
        assertThat(savedReview.getComment())
                .isEqualTo("Excellent delivery");

        assertThat(savedReview.getOrder().getId())
                .isEqualTo(orderId);

        assertThat(savedReview.getCourier().getId())
                .isEqualTo(order.getCourier().getId());

        assertThat(savedReview.getCreatedAt()).isNotNull();

        List<NewReviewEvent> events = applicationEvents
                .stream(NewReviewEvent.class)
                .toList();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).reviewId())
                .isEqualTo(savedReview.getId());
        assertThat(events.get(0).orderId()).isEqualTo(orderId);
        assertThat(events.get(0).rating()).isEqualTo(5);
        assertThat(events.get(0).courierId())
                .isEqualTo(order.getCourier().getId());
    }

    @Test
    void givenAnotherClientsOrder_whenReviewIsSubmitted_thenAccessIsDenied(ApplicationEvents applicationEvents) {

        // Given
        Order order = createOrder(OrderStatus.DELIVERED);

        // When / Then
        assertThatThrownBy(() -> reviewService.leaveReview(
                order.getId(),
                "9999",
                4,
                "Good")).isInstanceOf(SecurityException.class);

        assertThat(reviewRepository.existsByOrder_Id(order.getId()))
                .isFalse();

        assertThat(
                applicationEvents.stream(NewReviewEvent.class).count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SEARCHING_COURIER",
            "COURIER_ASSIGNED",
            "PICKED_UP"
    })
    void givenUndeliveredOrder_whenReviewIsSubmitted_thenRejected(
            String statusName, ApplicationEvents applicationEvents) {
        // Given
        OrderStatus status = OrderStatus.valueOf(statusName);
        Order order = createOrder(status);

        // When / Then
        assertThatThrownBy(() -> reviewService.leaveReview(
                order.getId(),
                "1001",
                4,
                "Good")).isInstanceOf(IllegalStateException.class);

        assertThat(reviewRepository.existsByOrder_Id(order.getId()))
                .isFalse();

        assertThat(
                applicationEvents.stream(NewReviewEvent.class).count()).isZero();
    }

    @Test
    void givenExistingReview_whenSecondReviewIsSubmitted_thenRejected(ApplicationEvents applicationEvents) {

        // Given
        Order order = createOrder(OrderStatus.DELIVERED);

        reviewService.leaveReview(
                order.getId(),
                "1001",
                5,
                "First review");

        // When / Then
        assertThatThrownBy(() -> reviewService.leaveReview(
                order.getId(),
                "1001",
                3,
                "Second review")).isInstanceOf(IllegalStateException.class);

        assertThat(reviewRepository.findByOrder_Id(order.getId()))
                .isPresent();

        assertThat(reviewRepository.count()).isEqualTo(1);

        assertThat(
                applicationEvents.stream(NewReviewEvent.class).count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 6, -1, 10 })
    void givenInvalidRating_whenReviewIsSubmitted_thenRejected(
            int rating, ApplicationEvents applicationEvents) {
        // Given
        Order order = createOrder(OrderStatus.DELIVERED);

        // When / Then
        assertThatThrownBy(() -> reviewService.leaveReview(
                order.getId(),
                "1001",
                rating,
                "Review")).isInstanceOf(IllegalArgumentException.class);

        assertThat(reviewRepository.existsByOrder_Id(order.getId()))
                .isFalse();

        assertThat(
                applicationEvents.stream(NewReviewEvent.class).count()).isZero();
    }

    @Test
    void givenMissingOrder_whenReviewIsSubmitted_thenNotFound(ApplicationEvents applicationEvents) {

        // Given
        Integer missingOrderId = 999999;

        // When / Then
        assertThatThrownBy(() -> reviewService.leaveReview(
                missingOrderId,
                "1001",
                5,
                "Review")).isInstanceOf(EntityNotFoundException.class);

        assertThat(
                applicationEvents.stream(NewReviewEvent.class).count()).isZero();
    }

    private Order createOrder(OrderStatus status) {

        Client client = new Client();
        client.setUserId("1001");
        client.setFullName("Test Client");
        client = clientRepository.saveAndFlush(client);

        Courier courier = new Courier();
        courier.setUserId("2001");
        courier.setFullName("Test Courier");
        courier.setStatus(CourierStatus.AVAILABLE);
        courier = courierRepository.saveAndFlush(courier);

        Zone zone = new Zone();
        zone.setName("Central Zone");
        zone = zoneRepository.saveAndFlush(zone);

        LocalDateTime now = LocalDateTime.now();

        Order order = new Order();
        order.setClient(client);
        order.setCourier(courier);
        order.setZone(zone);
        order.setPickupAddress("Pickup address");
        order.setDeliveryAddress("Delivery address");
        order.setStatus(status);

        order.addStatusHistory(OrderStatus.SEARCHING_COURIER, now.minusMinutes(15));

        if (status != OrderStatus.SEARCHING_COURIER) {
            order.addStatusHistory(OrderStatus.COURIER_ASSIGNED, now.minusMinutes(10));
        }

        if (status == OrderStatus.PICKED_UP
                || status == OrderStatus.DELIVERED) {
            order.addStatusHistory(OrderStatus.PICKED_UP, now.minusMinutes(5));
        }

        if (status == OrderStatus.DELIVERED) {
            order.addStatusHistory(OrderStatus.DELIVERED, now.minusMinutes(1));
        }

        return orderRepository.saveAndFlush(order);
    }
}
