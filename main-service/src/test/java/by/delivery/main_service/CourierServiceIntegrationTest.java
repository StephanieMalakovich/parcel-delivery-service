package by.delivery.main_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import by.delivery.main_service.event.OrderStatusChangedEvent;
import by.delivery.main_service.repository.ClientRepository;
import by.delivery.main_service.repository.CourierRepository;
import by.delivery.main_service.repository.OrderRepository;
import by.delivery.main_service.repository.ZoneRepository;
import by.delivery.main_service.repository.entity.Client;
import by.delivery.main_service.repository.entity.Courier;
import by.delivery.main_service.repository.entity.CourierStatus;
import by.delivery.main_service.repository.entity.Order;
import by.delivery.main_service.repository.entity.OrderStatus;
import by.delivery.main_service.repository.entity.Zone;
import by.delivery.main_service.service.CourierService;
import jakarta.persistence.EntityNotFoundException;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@RecordApplicationEvents
class CourierServiceIntegrationTest {

    @Autowired
    private CourierService courierService;

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
    void givenAssignedOrder_whenCourierPicksUp_thenStatusIsSaved() {

        // Given
        Order order = createOrder(OrderStatus.COURIER_ASSIGNED);

        Integer orderId = order.getId();
        LocalDateTime startedAt = LocalDateTime.now().minusSeconds(1);

        // When
        courierService.changeStatusByCourier(
                orderId,
                OrderStatus.PICKED_UP,
                2001);

        // Then
        Order saved = orderRepository.findById(orderId)
                .orElseThrow();

        assertThat(saved.getStatus())
                .isEqualTo(OrderStatus.PICKED_UP);

        assertThat(saved.getPickedUpAt())
                .isNotNull()
                .isAfterOrEqualTo(startedAt);

        assertThat(saved.getDeliveredAt()).isNull();

        List<OrderStatusChangedEvent> events = applicationEvents
                .stream(OrderStatusChangedEvent.class)
                .toList();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).orderId()).isEqualTo(orderId);
        assertThat(events.get(0).status())
                .isEqualTo(OrderStatus.PICKED_UP);
    }

    @Test
    void givenPickedUpOrder_whenCourierDelivers_thenStatusIsSaved() {

        // Given
        Order order = createOrder(OrderStatus.PICKED_UP);

        Integer orderId = order.getId();
        LocalDateTime originalPickedUpAt = orderRepository
                .findById(orderId)
                .orElseThrow()
                .getPickedUpAt();

        // When
        courierService.changeStatusByCourier(
                orderId,
                OrderStatus.DELIVERED,
                2001);

        // Then
        Order saved = orderRepository.findById(orderId)
                .orElseThrow();

        assertThat(saved.getStatus())
                .isEqualTo(OrderStatus.DELIVERED);

        assertThat(saved.getPickedUpAt())
                .isEqualTo(originalPickedUpAt);

        assertThat(saved.getDeliveredAt())
                .isNotNull()
                .isAfter(originalPickedUpAt);

        List<OrderStatusChangedEvent> events = applicationEvents
                .stream(OrderStatusChangedEvent.class)
                .toList();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).status())
                .isEqualTo(OrderStatus.DELIVERED);

        assertThat(events.get(0).pickedUpAt())
                .isEqualTo(originalPickedUpAt);

        assertThat(events.get(0).deliveredAt())
                .isEqualTo(saved.getDeliveredAt());

        assertThat(courierRepository.findById(order.getCourier().getId()).orElseThrow().getStatus())
                .isEqualTo(CourierStatus.AVAILABLE);
    }

    @Test
    void givenAnotherCouriersOrder_whenCourierPicksUp_thenAccessIsDenied() {

        // Given
        Order order = createOrder(OrderStatus.COURIER_ASSIGNED);

        Integer orderId = order.getId();

        createCourier(2002);

        // When / Then
        assertThatThrownBy(() -> courierService.changeStatusByCourier(
                orderId,
                OrderStatus.PICKED_UP,
                2002)).isInstanceOf(SecurityException.class);

        Order saved = orderRepository.findById(orderId)
                .orElseThrow();

        assertThat(saved.getStatus())
                .isEqualTo(OrderStatus.COURIER_ASSIGNED);

        assertThat(saved.getPickedUpAt()).isNull();
        assertThat(saved.getDeliveredAt()).isNull();

        assertThat(
                applicationEvents
                        .stream(OrderStatusChangedEvent.class)
                        .count())
                .isZero();
    }

    @Test
    void givenAssignedOrder_whenDeliveredBeforePickup_thenTransitionIsRejected() {

        // Given
        Order order = createOrder(OrderStatus.COURIER_ASSIGNED);

        Integer orderId = order.getId();

        // When / Then
        assertThatThrownBy(() -> courierService.changeStatusByCourier(
                orderId,
                OrderStatus.DELIVERED,
                2001)).isInstanceOf(IllegalStateException.class);

        Order saved = orderRepository.findById(orderId)
                .orElseThrow();

        assertThat(saved.getStatus())
                .isEqualTo(OrderStatus.COURIER_ASSIGNED);

        assertThat(saved.getPickedUpAt()).isNull();
        assertThat(saved.getDeliveredAt()).isNull();

        assertThat(
                applicationEvents
                        .stream(OrderStatusChangedEvent.class)
                        .count())
                .isZero();
    }

    @Test
    void givenAssignedOrder_whenAdminPicksUp_thenStatusIsSaved() {

        // Given
        Order order = createOrder(OrderStatus.COURIER_ASSIGNED);

        Integer orderId = order.getId();

        // When
        courierService.changeStatusByAdmin(
                orderId,
                OrderStatus.PICKED_UP);

        // Then
        Order saved = orderRepository.findById(orderId)
                .orElseThrow();

        assertThat(saved.getStatus())
                .isEqualTo(OrderStatus.PICKED_UP);

        assertThat(saved.getPickedUpAt()).isNotNull();

        assertThat(
                applicationEvents
                        .stream(OrderStatusChangedEvent.class)
                        .count())
                .isEqualTo(1);
    }

    @Test
    void givenMissingOrder_whenCourierPicksUp_thenNotFound() {

        // Given
        Integer missingOrderId = 999999;

        // When / Then
        assertThatThrownBy(() -> courierService.changeStatusByCourier(
                missingOrderId,
                OrderStatus.PICKED_UP,
                2001)).isInstanceOf(EntityNotFoundException.class);

        assertThat(
                applicationEvents
                        .stream(OrderStatusChangedEvent.class)
                        .count())
                .isZero();
    }

    private Order createOrder(OrderStatus initialStatus) {

        Client client = new Client();
        client.setUserId(1001);
        client.setFullName("Test Client");

        client = clientRepository.saveAndFlush(client);

        Courier courier = createCourier(2001);

        Zone zone = new Zone();
        zone.setName("Central Zone");

        zone = zoneRepository.saveAndFlush(zone);

        Order order = new Order();
        order.setClient(client);
        order.setCourier(courier);
        order.setZone(zone);

        order.setPickupAddress("Pickup address");
        order.setDeliveryAddress("Delivery address");
        order.setStatus(initialStatus);

        order.setAssignedAt(
                LocalDateTime.now()
                        .minusMinutes(10)
                        .truncatedTo(ChronoUnit.MICROS));

        if (initialStatus == OrderStatus.PICKED_UP) {
            order.setPickedUpAt(
                    LocalDateTime.now()
                            .minusMinutes(5)
                            .truncatedTo(ChronoUnit.MICROS));
        }

        return orderRepository.saveAndFlush(order);
    }

    private Courier createCourier(Integer userId) {
        Courier courier = new Courier();
        courier.setUserId(userId);
        courier.setFullName("Test Courier");
        courier.setStatus(CourierStatus.BUSY);   // курьер с назначенным заказом занят

        return courierRepository.saveAndFlush(courier);
    }
}
