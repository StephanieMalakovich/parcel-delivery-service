package by.delivery.main_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import by.delivery.main_service.dto.CreateOrderRequest;
import by.delivery.main_service.dto.OrderResponse;
import by.delivery.main_service.dto.ParcelRequest;
import by.delivery.main_service.event.NewOrderEvent;
import by.delivery.main_service.event.OrderAcceptedEvent;
import by.delivery.main_service.event.OrderCancelledEvent;
import by.delivery.main_service.event.OrderStatusChangedEvent;
import by.delivery.main_service.repository.ClientRepository;
import by.delivery.main_service.repository.CourierRepository;
import by.delivery.main_service.repository.CourierZoneRepository;
import by.delivery.main_service.repository.OrderRepository;
import by.delivery.main_service.repository.UserNotificationRepository;
import by.delivery.main_service.repository.ZoneRepository;
import by.delivery.main_service.repository.entity.CancelledBy;
import by.delivery.main_service.repository.entity.Client;
import by.delivery.main_service.repository.entity.Courier;
import by.delivery.main_service.repository.entity.CourierStatus;
import by.delivery.main_service.repository.entity.CourierZone;
import by.delivery.main_service.repository.entity.NotificationType;
import by.delivery.main_service.repository.entity.Order;
import by.delivery.main_service.repository.entity.OrderStatus;
import by.delivery.main_service.repository.entity.ParcelSize;
import by.delivery.main_service.repository.entity.Zone;
import by.delivery.main_service.service.OrderService;

import jakarta.persistence.EntityNotFoundException;

@SpringBootTest(properties = "spring.liquibase.contexts=test")
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class OrderServiceIntegrationTest {

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

    private static final AtomicInteger SEQ = new AtomicInteger(5000);

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private CourierRepository courierRepository;

    @Autowired
    private CourierZoneRepository courierZoneRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private UserNotificationRepository notificationRepository;

    @Autowired
    private TransactionTemplate tx;

    // ===================== создание заказа (4.3.2) =====================

    @Test
    void givenZoneWithCouriers_whenCreateOrder_thenOrderSavedAndZoneCouriersNotified(
            ApplicationEvents applicationEvents) {

        // Given
        Zone zone = newZone();
        Client client = newClient();
        Courier a = newCourier(zone);
        Courier b = newCourier(zone);
        Courier outside = newCourier(null);

        // When
        OrderResponse created = newOrder(client, zone);

        // Then
        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo(OrderStatus.SEARCHING_COURIER);
        assertThat(created.courierId()).isNull();
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.parcel().size()).isEqualTo(ParcelSize.SMALL);
        assertThat(created.parcel().weightKg()).isEqualByComparingTo("2.50");

        assertThat(countNotifications(a.getUserId(), NotificationType.NEW_ORDER_AVAILABLE)).isEqualTo(1);
        assertThat(countNotifications(b.getUserId(), NotificationType.NEW_ORDER_AVAILABLE)).isEqualTo(1);
        assertThat(countNotifications(outside.getUserId(), NotificationType.NEW_ORDER_AVAILABLE)).isZero();
        assertThat(countNotifications(client.getUserId(), NotificationType.NEW_ORDER_AVAILABLE)).isZero();

        assertThat(applicationEvents.stream(NewOrderEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.orderId()).isEqualTo(created.id());
                    assertThat(event.zoneId()).isEqualTo(zone.getId());
                    assertThat(event.clientId()).isEqualTo(client.getId());
                });
    }

    @Test
    void givenInvalidRequest_whenCreateOrder_thenRejectedAndNothingSaved(ApplicationEvents applicationEvents) {

        // Given
        Zone zone = newZone();
        Client client = newClient();
        long ordersBefore = orderRepository.count();
        ParcelRequest validParcel = new ParcelRequest(BigDecimal.ONE, ParcelSize.SMALL, null);

        // When / Then
        assertThatThrownBy(() -> orderService.createOrder(client.getUserId(),
                new CreateOrderRequest(zone.getId(), "A", "B",
                        new ParcelRequest(BigDecimal.ZERO, ParcelSize.SMALL, null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orderService.createOrder(client.getUserId(),
                new CreateOrderRequest(zone.getId(), " ", "B", validParcel)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orderService.createOrder(client.getUserId(),
                new CreateOrderRequest(zone.getId(), "A", "B", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orderService.createOrder("987654",
                new CreateOrderRequest(zone.getId(), "A", "B", validParcel)))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> orderService.createOrder(client.getUserId(),
                new CreateOrderRequest(987654, "A", "B", validParcel)))
                .isInstanceOf(EntityNotFoundException.class);

        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
        assertThat(applicationEvents.stream(NewOrderEvent.class)).isEmpty();
    }

    // ===================== принятие заявки курьером (4.3.3) =====================

    @Test
    void givenSearchingOrder_whenCourierAccepts_thenCourierAssignedAndBusy(ApplicationEvents applicationEvents) {

        // Given
        Zone zone = newZone();
        Client client = newClient();
        Courier courier = newCourier(zone);
        OrderResponse order = newOrder(client, zone);

        // When
        OrderResponse accepted = orderService.acceptOrder(courier.getUserId(), order.id());

        // Then
        assertThat(accepted.status()).isEqualTo(OrderStatus.COURIER_ASSIGNED);
        assertThat(accepted.courierId()).isEqualTo(courier.getId());
        assertThat(accepted.statusTime(OrderStatus.COURIER_ASSIGNED)).isNotNull();
        assertThat(courierStatus(courier)).isEqualTo(CourierStatus.BUSY);
        assertThat(countNotifications(client.getUserId(), NotificationType.ORDER_ASSIGNED)).isEqualTo(1);

        assertThat(applicationEvents.stream(OrderAcceptedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.orderId()).isEqualTo(order.id());
                    assertThat(event.courierUserId()).isEqualTo(courier.getUserId());
                    assertThat(event.clientUserId()).isEqualTo(client.getUserId());
                });
    }

    @Test
    void givenOneOrder_whenTwoCouriersAcceptSimultaneously_thenExactlyOneWins() throws Exception {

        // Given
        Zone zone = newZone();
        Client client = newClient();
        Courier a = newCourier(zone);
        Courier b = newCourier(zone);
        OrderResponse order = newOrder(client, zone);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        // When
        List<Future<Boolean>> results = List.of(a, b).stream()
                .map(courier -> pool.submit((Callable<Boolean>) () -> {
                    start.await();
                    try {
                        orderService.acceptOrder(courier.getUserId(), order.id());
                        return true;
                    } catch (IllegalStateException e) {
                        return false;
                    }
                }))
                .toList();
        start.countDown();

        int wins = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                wins++;
            }
        }
        pool.shutdown();

        // Then
        assertThat(wins).isEqualTo(1);
        assertThat(orderRepository.findById(order.id()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COURIER_ASSIGNED);
        long busyCouriers = List.of(a, b).stream()
                .filter(courier -> courierStatus(courier) == CourierStatus.BUSY)
                .count();
        assertThat(busyCouriers).isEqualTo(1);
    }

    @Test
    void givenInvalidConditions_whenCourierAccepts_thenRejected() {

        // Given
        Zone zone = newZone();
        Client client = newClient();
        Courier inZone = newCourier(zone);
        Courier otherZone = newCourier(newZone());
        OrderResponse first = newOrder(client, zone);
        OrderResponse second = newOrder(client, zone);

        // When / Then: курьер не обслуживает зону
        assertThatThrownBy(() -> orderService.acceptOrder(otherZone.getUserId(), first.id()))
                .isInstanceOf(IllegalStateException.class);
        // When / Then: заказ или курьер не существуют
        assertThatThrownBy(() -> orderService.acceptOrder(inZone.getUserId(), 987654))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> orderService.acceptOrder("987654", first.id()))
                .isInstanceOf(EntityNotFoundException.class);

        // When / Then: занятый курьер не может взять второй заказ
        orderService.acceptOrder(inZone.getUserId(), first.id());
        assertThatThrownBy(() -> orderService.acceptOrder(inZone.getUserId(), second.id()))
                .isInstanceOf(IllegalStateException.class);

        // When / Then: уже назначенный заказ нельзя взять повторно
        Courier another = newCourier(zone);
        assertThatThrownBy(() -> orderService.acceptOrder(another.getUserId(), first.id()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(orderRepository.findById(second.id()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.SEARCHING_COURIER);
    }

    // ===================== управление администратором (4.3.8)
    // =====================

    @Test
    void givenAssignedOrder_whenAdminReassigns_thenCouriersSwappedAndNotified(ApplicationEvents applicationEvents) {

        // Given
        Zone zone = newZone();
        Client client = newClient();
        Courier a = newCourier(zone);
        Courier b = newCourier(zone);
        OrderResponse order = newOrder(client, zone);
        orderService.acceptOrder(a.getUserId(), order.id());

        // When
        OrderResponse reassigned = orderService.reassignOrderByAdmin(order.id(), b.getId());

        // Then
        assertThat(reassigned.courierId()).isEqualTo(b.getId());
        assertThat(reassigned.status()).isEqualTo(OrderStatus.COURIER_ASSIGNED);
        assertThat(courierStatus(a)).isEqualTo(CourierStatus.AVAILABLE);
        assertThat(courierStatus(b)).isEqualTo(CourierStatus.BUSY);
        assertThat(countNotifications(a.getUserId(), NotificationType.ORDER_CANCELLED)).isEqualTo(1);
        assertThat(countNotifications(b.getUserId(), NotificationType.ORDER_ASSIGNED)).isEqualTo(1);
        assertThat(countNotifications(client.getUserId(), NotificationType.ORDER_STATUS_CHANGED)).isEqualTo(1);

        assertThat(applicationEvents.stream(OrderStatusChangedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.previousCourierUserId()).isEqualTo(a.getUserId());
                    assertThat(event.courierUserId()).isEqualTo(b.getUserId());
                });
        assertThat(applicationEvents.stream(OrderCancelledEvent.class)).isEmpty();
    }

    @Test
    void givenSearchingAndPickedUpOrders_whenAdminReassigns_thenAllowedOnlyForValidCouriers() {

        // Given
        Zone zone = newZone();
        Client client = newClient();
        Courier a = newCourier(zone);
        Courier b = newCourier(zone);
        OrderResponse searching = newOrder(client, zone);

        // When: назначение на заказ без курьера
        OrderResponse assigned = orderService.reassignOrderByAdmin(searching.id(), a.getId());

        // Then
        assertThat(assigned.status()).isEqualTo(OrderStatus.COURIER_ASSIGNED);
        assertThat(assigned.statusTime(OrderStatus.COURIER_ASSIGNED)).isNotNull();

        // When / Then: тот же курьер, занятый курьер и курьер чужой зоны отклоняются
        assertThatThrownBy(() -> orderService.reassignOrderByAdmin(searching.id(), a.getId()))
                .isInstanceOf(IllegalStateException.class);

        Courier busy = newCourier(zone);
        OrderResponse other = newOrder(client, zone);
        orderService.acceptOrder(busy.getUserId(), other.id());
        assertThatThrownBy(() -> orderService.reassignOrderByAdmin(searching.id(), busy.getId()))
                .isInstanceOf(IllegalStateException.class);

        Courier foreign = newCourier(newZone());
        assertThatThrownBy(() -> orderService.reassignOrderByAdmin(searching.id(), foreign.getId()))
                .isInstanceOf(IllegalStateException.class);

        // When: переназначение уже забранной посылки
        tx.executeWithoutResult(status -> {
            Order order = orderRepository.findById(searching.id()).orElseThrow();
            order.setStatus(OrderStatus.PICKED_UP);
        });
        OrderResponse swapped = orderService.reassignOrderByAdmin(searching.id(), b.getId());

        // Then: статус PICKED_UP сохраняется
        assertThat(swapped.status()).isEqualTo(OrderStatus.PICKED_UP);
        assertThat(swapped.courierId()).isEqualTo(b.getId());
    }

    @Test
    void givenAssignedOrder_whenAdminCancels_thenCourierFreedAndBothEventsPublished(
            ApplicationEvents applicationEvents) {

        // Given
        Zone zone = newZone();
        Client client = newClient();
        Courier courier = newCourier(zone);
        OrderResponse order = newOrder(client, zone);
        orderService.acceptOrder(courier.getUserId(), order.id());

        // When
        OrderResponse cancelled = orderService.cancelOrderByAdmin(order.id());

        // Then
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.cancelledBy()).isEqualTo(CancelledBy.ADMIN);
        assertThat(cancelled.statusTime(OrderStatus.CANCELLED)).isNotNull();
        assertThat(cancelled.courierId()).isEqualTo(courier.getId());
        assertThat(courierStatus(courier)).isEqualTo(CourierStatus.AVAILABLE);
        assertThat(countNotifications(courier.getUserId(), NotificationType.ORDER_CANCELLED)).isEqualTo(1);
        assertThat(countNotifications(client.getUserId(), NotificationType.ORDER_STATUS_CHANGED)).isEqualTo(1);

        assertThat(applicationEvents.stream(OrderStatusChangedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.status()).isEqualTo(OrderStatus.CANCELLED);
                    assertThat(event.previousCourierUserId()).isEqualTo(courier.getUserId());
                    assertThat(event.courierUserId()).isNull();
                });
        assertThat(applicationEvents.stream(OrderCancelledEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.cancelledBy()).isEqualTo(CancelledBy.ADMIN);
                    assertThat(event.courierId()).isEqualTo(courier.getId());
                });

        // Then: завершённый заказ изменять нельзя
        assertThatThrownBy(() -> orderService.cancelOrderByAdmin(order.id()))
                .isInstanceOf(IllegalStateException.class);
        Courier other = newCourier(zone);
        assertThatThrownBy(() -> orderService.reassignOrderByAdmin(order.id(), other.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givenOrderWithoutCourier_whenAdminCancels_thenCancelledWithoutCourier(ApplicationEvents applicationEvents) {

        // Given
        Zone zone = newZone();
        Client client = newClient();
        OrderResponse order = newOrder(client, zone);

        // When
        OrderResponse cancelled = orderService.cancelOrderByAdmin(order.id());

        // Then
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.courierId()).isNull();
        assertThat(applicationEvents.stream(OrderCancelledEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.courierId()).isNull());
        assertThatThrownBy(() -> orderService.cancelOrderByAdmin(987654))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ===================== вспомогательные методы =====================

    private Zone newZone() {
        Zone zone = new Zone();
        zone.setName("Тест-зона-" + SEQ.incrementAndGet());
        return zoneRepository.save(zone);
    }

    private Client newClient() {
        Client client = new Client();
        client.setUserId(String.valueOf(SEQ.incrementAndGet()));
        client.setFullName("Клиент");
        return clientRepository.save(client);
    }

    private Courier newCourier(Zone zone) {
        return tx.execute(status -> {
            Courier courier = new Courier();
            courier.setUserId(String.valueOf(SEQ.incrementAndGet()));
            courier.setFullName("Курьер " + courier.getUserId());
            courierRepository.save(courier);
            if (zone != null) {
                courierZoneRepository.save(new CourierZone(courier, zone));
            }
            return courier;
        });
    }

    private OrderResponse newOrder(Client client, Zone zone) {
        return orderService.createOrder(client.getUserId(), new CreateOrderRequest(
                zone.getId(),
                "ул. Молодогвардейская 151",
                "Московское шоссе 34",
                new ParcelRequest(new BigDecimal("2.50"), ParcelSize.SMALL, "Документы")));
    }

    private CourierStatus courierStatus(Courier courier) {
        return courierRepository.findById(courier.getId()).orElseThrow().getStatus();
    }

    private long countNotifications(String userId, NotificationType type) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 50))
                .getContent()
                .stream()
                .filter(notification -> notification.getType() == type)
                .count();
    }
}
