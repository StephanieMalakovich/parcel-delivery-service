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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
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
import by.delivery.main_service.repository.entity.CancellBy;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@RecordApplicationEvents
class OrderServiceIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(5000);

    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired CourierRepository courierRepository;
    @Autowired CourierZoneRepository courierZoneRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired UserNotificationRepository notificationRepository;
    @Autowired TransactionTemplate tx;


    private Zone newZone() {
        Zone z = new Zone();
        z.setName("Тест-зона-" + SEQ.incrementAndGet());
        return zoneRepository.save(z);
    }

    private Client newClient() {
        Client c = new Client();
        c.setUserId(SEQ.incrementAndGet());
        c.setFullName("Клиент");
        return clientRepository.save(c);
    }

    private Courier newCourier(Zone zone) {
        return tx.execute(status -> {
            Courier c = new Courier();
            c.setUserId(SEQ.incrementAndGet());
            c.setFullName("Курьер " + c.getUserId());
            courierRepository.save(c);
            if (zone != null) {
                courierZoneRepository.save(new CourierZone(c, zone));
            }
            return c;
        });
    }

    private OrderResponse newOrder(Client client, Zone zone) {
        return orderService.createOrder(client.getUserId(), new CreateOrderRequest(zone.getId(),
                "ул. Молодогвардейская 151", "Московское шоссе 34", new ParcelRequest(new BigDecimal("2.50"), ParcelSize.SMALL, "Документы")));
    }

    private CourierStatus courierStatus(Courier c) {
        return courierRepository.findById(c.getId()).orElseThrow().getStatus();
    }

    private long unread(Integer userId, NotificationType type) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 50)).getContent()
                .stream().filter(n -> n.getType() == type).count();
    }


    @Test
    void createOrder_savesOrderAndParcel_notifiesZoneCouriers_firesEvent(ApplicationEvents applicationEvents) {
        Zone zone = newZone();
        Client client = newClient();
        Courier a = newCourier(zone);
        Courier b = newCourier(zone);
        Courier outside = newCourier(null);

        OrderResponse created = newOrder(client, zone);

        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo(OrderStatus.SEARCHING_COURIER);
        assertThat(created.courierId()).isNull();
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.parcel().size()).isEqualTo(ParcelSize.SMALL);
        assertThat(created.parcel().weightKg()).isEqualByComparingTo("2.50");

        assertThat(unread(a.getUserId(), NotificationType.NEW_ORDER_AVAILABLE)).isEqualTo(1);
        assertThat(unread(b.getUserId(), NotificationType.NEW_ORDER_AVAILABLE)).isEqualTo(1);
        assertThat(unread(outside.getUserId(), NotificationType.NEW_ORDER_AVAILABLE)).isZero();
        assertThat(unread(client.getUserId(), NotificationType.NEW_ORDER_AVAILABLE)).isZero();

        assertThat(applicationEvents.stream(NewOrderEvent.class))
                .singleElement().satisfies(e -> assertThat(e.zoneId()).isEqualTo(zone.getId()));
    }

    @Test
    void createOrder_withInvalidDataOrUnknownReferences_savesNothing(ApplicationEvents applicationEvents) {
        Zone zone = newZone();
        Client client = newClient();
        long before = orderRepository.count();
        ParcelRequest ok = new ParcelRequest(BigDecimal.ONE, ParcelSize.SMALL, null);

        assertThatThrownBy(() -> orderService.createOrder(client.getUserId(),
                new CreateOrderRequest(zone.getId(), "A", "B", new ParcelRequest(BigDecimal.ZERO, ParcelSize.SMALL, null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orderService.createOrder(client.getUserId(),
                new CreateOrderRequest(zone.getId(), " ", "B", ok))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orderService.createOrder(client.getUserId(),
                new CreateOrderRequest(zone.getId(), "A", "B", null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orderService.createOrder(987654,
                new CreateOrderRequest(zone.getId(), "A", "B", ok))).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> orderService.createOrder(client.getUserId(),
                new CreateOrderRequest(987654, "A", "B", ok))).isInstanceOf(EntityNotFoundException.class);

        assertThat(orderRepository.count()).isEqualTo(before);
        assertThat(applicationEvents.stream(NewOrderEvent.class)).isEmpty();
    }


    @Test
    void acceptOrder_assignsCourier_marksBusy_notifiesClient_firesEvent(ApplicationEvents applicationEvents) {
        Zone zone = newZone();
        Client client = newClient();
        Courier courier = newCourier(zone);
        OrderResponse order = newOrder(client, zone);

        OrderResponse accepted = orderService.acceptOrder(courier.getUserId(), order.id());

        assertThat(accepted.status()).isEqualTo(OrderStatus.COURIER_ASSIGNED);
        assertThat(accepted.courierId()).isEqualTo(courier.getId());
        assertThat(accepted.assignedAt()).isNotNull();
        assertThat(courierStatus(courier)).isEqualTo(CourierStatus.BUSY);
        assertThat(unread(client.getUserId(), NotificationType.ORDER_ASSIGNED)).isEqualTo(1);
        assertThat(applicationEvents.stream(OrderAcceptedEvent.class))
                .singleElement().satisfies(e -> {
                    assertThat(e.orderId()).isEqualTo(order.id());
                    assertThat(e.courierUserId()).isEqualTo(courier.getUserId());
                    assertThat(e.clientUserId()).isEqualTo(client.getUserId());
                });
    }

    @Test
    void acceptOrder_whenTwoCouriersRace_exactlyOneWins() throws Exception {
        Zone zone = newZone();
        Client client = newClient();
        Courier a = newCourier(zone);
        Courier b = newCourier(zone);
        OrderResponse order = newOrder(client, zone);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = List.of(a, b).stream()
                .map(c -> pool.submit((Callable<Boolean>) () -> {
                    start.await();
                    try {
                        orderService.acceptOrder(c.getUserId(), order.id());
                        return true;
                    } catch (IllegalStateException e) {
                        return false;
                    }
                })).toList();
        start.countDown();
        int wins = 0;
        for (Future<Boolean> f : results) {
            if (f.get()) wins++;
        }
        pool.shutdown();

        assertThat(wins).isEqualTo(1);
        Order db = orderRepository.findById(order.id()).orElseThrow();
        assertThat(db.getStatus()).isEqualTo(OrderStatus.COURIER_ASSIGNED);
        long busy = List.of(a, b).stream().filter(c -> courierStatus(c) == CourierStatus.BUSY).count();
        assertThat(busy).isEqualTo(1);
    }

    @Test
    void acceptOrder_rejectsInvalidCases() {
        Zone zone = newZone();
        Client client = newClient();
        Courier inZone = newCourier(zone);
        Courier otherZone = newCourier(newZone());
        OrderResponse first = newOrder(client, zone);
        OrderResponse second = newOrder(client, zone);

        assertThatThrownBy(() -> orderService.acceptOrder(otherZone.getUserId(), first.id()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> orderService.acceptOrder(inZone.getUserId(), 987654))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> orderService.acceptOrder(987654, first.id()))
                .isInstanceOf(EntityNotFoundException.class);

        orderService.acceptOrder(inZone.getUserId(), first.id());
        assertThatThrownBy(() -> orderService.acceptOrder(inZone.getUserId(), second.id()))
                .isInstanceOf(IllegalStateException.class);
        Courier another = newCourier(zone);
        assertThatThrownBy(() -> orderService.acceptOrder(another.getUserId(), first.id()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(orderRepository.findById(second.id()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.SEARCHING_COURIER);
    }


    @Test
    void adminReassign_swapsCouriers_notifiesAll_firesStatusChanged(ApplicationEvents applicationEvents) {
        Zone zone = newZone();
        Client client = newClient();
        Courier a = newCourier(zone);
        Courier b = newCourier(zone);
        OrderResponse order = newOrder(client, zone);
        orderService.acceptOrder(a.getUserId(), order.id());

        OrderResponse re = orderService.reassignOrderByAdmin(order.id(), b.getId());

        assertThat(re.courierId()).isEqualTo(b.getId());
        assertThat(re.status()).isEqualTo(OrderStatus.COURIER_ASSIGNED);
        assertThat(courierStatus(a)).isEqualTo(CourierStatus.AVAILABLE);
        assertThat(courierStatus(b)).isEqualTo(CourierStatus.BUSY);
        assertThat(unread(a.getUserId(), NotificationType.ORDER_CANCELLED)).isEqualTo(1);
        assertThat(unread(b.getUserId(), NotificationType.ORDER_ASSIGNED)).isEqualTo(1);
        assertThat(unread(client.getUserId(), NotificationType.ORDER_STATUS_CHANGED)).isEqualTo(1);
        assertThat(applicationEvents.stream(OrderStatusChangedEvent.class))
                .singleElement().satisfies(e -> {
                    assertThat(e.previousCourierUserId()).isEqualTo(a.getUserId());
                    assertThat(e.courierUserId()).isEqualTo(b.getUserId());
                });
        assertThat(applicationEvents.stream(OrderCancelledEvent.class)).isEmpty();
    }

    @Test
    void adminReassign_worksForSearchingAndPickedUpOrders_andRejectsInvalidCases() {
        Zone zone = newZone();
        Client client = newClient();
        Courier a = newCourier(zone);
        Courier b = newCourier(zone);

        OrderResponse searching = newOrder(client, zone);
        OrderResponse assigned = orderService.reassignOrderByAdmin(searching.id(), a.getId());
        assertThat(assigned.status()).isEqualTo(OrderStatus.COURIER_ASSIGNED);
        assertThat(assigned.assignedAt()).isNotNull();

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

        tx.executeWithoutResult(s -> {
            Order o = orderRepository.findById(searching.id()).orElseThrow();
            o.setStatus(OrderStatus.PICKED_UP);
        });
        OrderResponse swapped = orderService.reassignOrderByAdmin(searching.id(), b.getId());
        assertThat(swapped.status()).isEqualTo(OrderStatus.PICKED_UP);
        assertThat(swapped.courierId()).isEqualTo(b.getId());
    }

    @Test
    void adminCancel_withCourier_setsCancelledByAdmin_freesCourier_firesBothEvents(ApplicationEvents applicationEvents) {
        Zone zone = newZone();
        Client client = newClient();
        Courier courier = newCourier(zone);
        OrderResponse order = newOrder(client, zone);
        orderService.acceptOrder(courier.getUserId(), order.id());

        OrderResponse cancelled = orderService.cancelOrderByAdmin(order.id());

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.cancelledBy()).isEqualTo(CancellBy.ADMIN);
        assertThat(cancelled.cancelledAt()).isNotNull();
        assertThat(cancelled.courierId()).isEqualTo(courier.getId());
        assertThat(courierStatus(courier)).isEqualTo(CourierStatus.AVAILABLE);
        assertThat(unread(courier.getUserId(), NotificationType.ORDER_CANCELLED)).isEqualTo(1);
        assertThat(unread(client.getUserId(), NotificationType.ORDER_STATUS_CHANGED)).isEqualTo(1);
        assertThat(applicationEvents.stream(OrderStatusChangedEvent.class))
                .singleElement().satisfies(e -> {
                    assertThat(e.status()).isEqualTo(OrderStatus.CANCELLED);
                    assertThat(e.previousCourierUserId()).isEqualTo(courier.getUserId());
                    assertThat(e.courierUserId()).isNull();
                });
        assertThat(applicationEvents.stream(OrderCancelledEvent.class))
                .singleElement().satisfies(e -> {
                    assertThat(e.cancelledBy()).isEqualTo(CancellBy.ADMIN);
                    assertThat(e.courierId()).isEqualTo(courier.getId());
                });

        assertThatThrownBy(() -> orderService.cancelOrderByAdmin(order.id())).isInstanceOf(IllegalStateException.class);
        Courier other = newCourier(zone);
        assertThatThrownBy(() -> orderService.reassignOrderByAdmin(order.id(), other.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void adminCancel_beforeAssignment_hasNoCourier(ApplicationEvents applicationEvents) {
        Zone zone = newZone();
        Client client = newClient();
        OrderResponse order = newOrder(client, zone);

        OrderResponse cancelled = orderService.cancelOrderByAdmin(order.id());

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.courierId()).isNull();
        assertThat(applicationEvents.stream(OrderCancelledEvent.class))
                .singleElement().satisfies(e -> assertThat(e.courierId()).isNull());
        assertThatThrownBy(() -> orderService.cancelOrderByAdmin(987654)).isInstanceOf(EntityNotFoundException.class);
    }
}
