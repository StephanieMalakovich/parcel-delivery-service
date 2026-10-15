package by.delivery.main_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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

import by.delivery.main_service.event.OrderCancelledEvent;
import by.delivery.main_service.repository.ClientRepository;
import by.delivery.main_service.repository.CourierRepository;
import by.delivery.main_service.repository.OrderRepository;
import by.delivery.main_service.repository.ZoneRepository;
import by.delivery.main_service.repository.entity.CancelledBy;
import by.delivery.main_service.repository.entity.Client;
import by.delivery.main_service.repository.entity.Courier;
import by.delivery.main_service.repository.entity.CourierStatus;
import by.delivery.main_service.repository.entity.NotificationType;
import by.delivery.main_service.repository.entity.Order;
import by.delivery.main_service.repository.entity.OrderStatus;
import by.delivery.main_service.repository.entity.Zone;
import by.delivery.main_service.service.OrderService;
import jakarta.persistence.EntityNotFoundException;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.sql.init.mode=never"
})
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class OrderCancellationIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private CourierRepository courierRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents applicationEvents;

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

    @Test
    void givenSearchingOrder_whenClientCancels_thenOrderIsCancelled() {

        // Given
        Order order = createOrder(OrderStatus.SEARCHING_COURIER);
        Integer orderId = order.getId();

        // When
        orderService.cancelOrderByClient(1001, orderId);

        // Then
        Order saved = orderRepository.findById(orderId)
                .orElseThrow();

        assertThat(saved.getStatus())
                .isEqualTo(OrderStatus.CANCELLED);

        assertThat(saved.getCancelledBy())
                .isEqualTo(CancelledBy.CLIENT);

        assertThat(saved.getCancelledAt()).isNotNull();
        assertThat(saved.getCourier()).isNull();

        OrderCancelledEvent event = applicationEvents
                .stream(OrderCancelledEvent.class)
                .findFirst()
                .orElseThrow();

        assertThat(event.orderId()).isEqualTo(orderId);
        assertThat(event.cancelledBy()).isEqualTo(CancelledBy.CLIENT);

        assertThat(
                applicationEvents.stream(OrderCancelledEvent.class).count()
        ).isEqualTo(1);

        assertThat(countNotifications(2001)).isZero();
    }

    @Test
    void givenAssignedOrder_whenClientCancels_thenCourierIsReleased() {

        // Given
        Order order = createOrder(OrderStatus.COURIER_ASSIGNED);
        Integer orderId = order.getId();
        Integer courierId = order.getCourier().getId();

        // When
        orderService.cancelOrderByClient(1001, orderId);

        // Then
        Order saved = orderRepository.findById(orderId)
                .orElseThrow();

        assertThat(saved.getStatus())
                .isEqualTo(OrderStatus.CANCELLED);

        assertThat(saved.getCancelledBy())
                .isEqualTo(CancelledBy.CLIENT);

        assertThat(saved.getCancelledAt()).isNotNull();

        Integer persistedCourierId = jdbcTemplate.queryForObject(
                "SELECT courier_id FROM orders WHERE id = ?",
                Integer.class,
                orderId
        );

        assertThat(persistedCourierId).isEqualTo(courierId);

        Courier courier = courierRepository.findById(courierId)
                .orElseThrow();

        assertThat(courier.getStatus())
                .isEqualTo(CourierStatus.AVAILABLE);

        assertThat(countNotifications(2001)).isEqualTo(1);

        Long cancellationNotifications = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM notifications
                WHERE user_id = ?
                  AND type = ?
                """,
                Long.class,
                2001,
                NotificationType.ORDER_CANCELLED.name()
        );

        assertThat(cancellationNotifications).isEqualTo(1L);

        OrderCancelledEvent event = applicationEvents
                .stream(OrderCancelledEvent.class)
                .findFirst()
                .orElseThrow();

        assertThat(event.cancelledBy()).isEqualTo(CancelledBy.CLIENT);
    }

    @Test
    void givenAnotherClientsOrder_whenClientCancels_thenAccessIsDenied() {

        // Given
        Order order = createOrder(OrderStatus.SEARCHING_COURIER);
        Integer orderId = order.getId();

        // When / Then
        assertThatThrownBy(() ->
                orderService.cancelOrderByClient(9999, orderId)
        ).isInstanceOf(SecurityException.class);

        // Данные остались прежними.
        Order saved = orderRepository.findById(orderId)
                .orElseThrow();

        assertThat(saved.getStatus())
                .isEqualTo(OrderStatus.SEARCHING_COURIER);

        assertThat(saved.getCancelledBy()).isNull();
        assertThat(saved.getCancelledAt()).isNull();

        assertThat(
                applicationEvents.stream(OrderCancelledEvent.class).count()
        ).isZero();

        assertThat(countNotifications(2001)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "PICKED_UP",
            "DELIVERED",
            "CANCELLED"
    })
    void givenNonCancellableOrder_whenClientCancels_thenRejected(
            String statusName
    ) {
        // Given
        OrderStatus initialStatus = OrderStatus.valueOf(statusName);
        Order order = createOrder(initialStatus);
        Integer orderId = order.getId();

        // When / Then
        assertThatThrownBy(() ->
                orderService.cancelOrderByClient(1001, orderId)
        ).isInstanceOf(IllegalStateException.class);

        // Исходное состояние не изменено.
        Order saved = orderRepository.findById(orderId)
                .orElseThrow();

        assertThat(saved.getStatus()).isEqualTo(initialStatus);

        assertThat(
                applicationEvents.stream(OrderCancelledEvent.class).count()
        ).isZero();
    }

    @Test
    void givenMissingOrder_whenClientCancels_thenNotFound() {

        // Given
        Integer missingOrderId = 999999;

        // When / Then
        assertThatThrownBy(() ->
                orderService.cancelOrderByClient(1001, missingOrderId)
        ).isInstanceOf(EntityNotFoundException.class);

        assertThat(
                applicationEvents.stream(OrderCancelledEvent.class).count()
        ).isZero();
    }

    private long countNotifications(Integer userId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM notifications
                WHERE user_id = ?
                """,
                Long.class,
                userId
        );

        return count == null ? 0 : count;
    }

    private Order createOrder(OrderStatus status) {

        Client client = new Client();
        client.setUserId(1001);
        client.setFullName("Test Client");
        client = clientRepository.saveAndFlush(client);

        Courier courier = null;

        if (status != OrderStatus.SEARCHING_COURIER) {
            courier = createCourier(2001);
        }

        Zone zone = new Zone();
        zone.setName("Central Zone");
        zone = zoneRepository.saveAndFlush(zone);

        LocalDateTime now = LocalDateTime.now()
                .truncatedTo(ChronoUnit.MICROS);

        Order order = new Order();
        order.setClient(client);
        order.setCourier(courier);
        order.setZone(zone);
        order.setPickupAddress("Pickup address");
        order.setDeliveryAddress("Delivery address");
        order.setStatus(status);

        if (courier != null) {
            order.setAssignedAt(now.minusMinutes(10));
        }

        if (status == OrderStatus.PICKED_UP
                || status == OrderStatus.DELIVERED) {
            order.setPickedUpAt(now.minusMinutes(5));
        }

        if (status == OrderStatus.DELIVERED) {
            order.setDeliveredAt(now.minusMinutes(1));
        }

        if (status == OrderStatus.CANCELLED) {
            order.setCancelledBy(CancelledBy.CLIENT);
            order.setCancelledAt(now.minusMinutes(1));
        }

        return orderRepository.saveAndFlush(order);
    }

    private Courier createCourier(Integer userId) {
        Courier courier = new Courier();
        courier.setUserId(userId);
        courier.setFullName("Test Courier");
        courier.setStatus(CourierStatus.BUSY);

        return courierRepository.saveAndFlush(courier);
    }
}
