package by.delivery.main_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import by.delivery.main_service.event.NewOrderEvent;
import by.delivery.main_service.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import by.delivery.main_service.repository.ClientRepository;
import by.delivery.main_service.repository.CourierRepository;
import by.delivery.main_service.repository.CourierZoneRepository;
import by.delivery.main_service.repository.OrderRepository;
import by.delivery.main_service.repository.UserNotificationRepository;
import by.delivery.main_service.repository.ZoneRepository;
import by.delivery.main_service.repository.entity.Client;
import by.delivery.main_service.repository.entity.Courier;
import by.delivery.main_service.repository.entity.CourierZone;
import by.delivery.main_service.repository.entity.NotificationType;
import by.delivery.main_service.repository.entity.Order;
import by.delivery.main_service.repository.entity.OrderStatus;
import by.delivery.main_service.repository.entity.ParcelSize;
import by.delivery.main_service.repository.entity.Zone;
import jakarta.persistence.EntityNotFoundException;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@RecordApplicationEvents
class OrderServiceIntegrationTest {

    @Autowired
    OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired CourierRepository courierRepository;
    @Autowired CourierZoneRepository courierZoneRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired UserNotificationRepository notificationRepository;
    @Autowired TransactionTemplate tx;

    private Zone newZone(String name) {
        Zone z = new Zone();
        z.setName(name);
        return zoneRepository.save(z);
    }

    private Client newClient(int userId) {
        Client c = new Client();
        c.setUserId(userId);
        c.setFullName("Клиент " + userId);
        return clientRepository.save(c);
    }

    private Courier newCourierInZone(int userId, Zone zone) {
        return tx.execute(status -> {
            Courier c = new Courier();
            c.setUserId(userId);
            c.setFullName("Курьер " + userId);
            courierRepository.save(c);
            if (zone != null) {
                courierZoneRepository.save(new CourierZone(c, zone));
            }
            return c;
        });
    }

    @Test
    void createOrder_savesOrderAndParcel_notifiesZoneCouriers_firesNewOrderEvent(ApplicationEvents applicationEvents) {
        Zone zone = newZone("Тест-зона-1");
        newClient(1001);
        newCourierInZone(2001, zone);
        newCourierInZone(2002, zone);
        newCourierInZone(2003, null);

        Order created = orderService.createOrder(1001, zone.getId(), "ул. Цайль 10", "Банкштрассе 3",
                new BigDecimal("2.50"), ParcelSize.SMALL, "Документы");

        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(OrderStatus.SEARCHING_COURIER);
        assertThat(created.getCreatedAt()).isNotNull();

        tx.executeWithoutResult(status -> {
            Order db = orderRepository.findById(created.getId()).orElseThrow();
            assertThat(db.getCourier()).isNull();
            assertThat(db.getPickupAddress()).isEqualTo("ул. Цайль 10");
            assertThat(db.getParcel()).isNotNull();
            assertThat(db.getParcel().getSize()).isEqualTo(ParcelSize.SMALL);
            assertThat(db.getParcel().getWeightKg()).isEqualByComparingTo("2.50");
        });

        var page = PageRequest.of(0, 10);
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(2001, page).getContent())
                .singleElement().satisfies(n -> {
                    assertThat(n.getType()).isEqualTo(NotificationType.NEW_ORDER_AVAILABLE);
                    assertThat(n.getIsRead()).isFalse();
                    assertThat(n.getText()).contains("#" + created.getId());
                });
        assertThat(notificationRepository.countByUserIdAndIsReadFalse(2002)).isEqualTo(1);
        assertThat(notificationRepository.countByUserIdAndIsReadFalse(2003)).isZero();
        assertThat(notificationRepository.countByUserIdAndIsReadFalse(1001)).isZero();

        // событие «новый заказ» сформировано ровно один раз (его слушатель после коммита пишет в лог)
        assertThat(applicationEvents.stream(NewOrderEvent.class))
                .filteredOn(e -> e.orderId().equals(created.getId()))
                .singleElement().satisfies(e -> {
                    assertThat(e.zoneId()).isEqualTo(zone.getId());
                    assertThat(e.status()).isEqualTo(OrderStatus.SEARCHING_COURIER);
                    assertThat(e.type()).isEqualTo(NewOrderEvent.TYPE);
                });
    }

    @Test
    void createOrder_withInvalidData_savesNothing(ApplicationEvents applicationEvents) {
        Zone zone = newZone("Тест-зона-2");
        newClient(1002);
        long before = orderRepository.count();

        assertThatThrownBy(() -> orderService.createOrder(1002, zone.getId(), "A", "B",
                BigDecimal.ZERO, ParcelSize.SMALL, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orderService.createOrder(1002, zone.getId(), " ", "B",
                BigDecimal.ONE, ParcelSize.SMALL, null)).isInstanceOf(IllegalArgumentException.class);

        assertThat(orderRepository.count()).isEqualTo(before);
        assertThat(applicationEvents.stream(NewOrderEvent.class)).isEmpty();
    }

    @Test
    void createOrder_withoutClientProfileOrWithUnknownZone_fails() {
        Zone zone = newZone("Тест-зона-3");
        newClient(1003);

        assertThatThrownBy(() -> orderService.createOrder(9999, zone.getId(), "A", "B",
                BigDecimal.ONE, ParcelSize.SMALL, null)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> orderService.createOrder(1003, 987654, "A", "B",
                BigDecimal.ONE, ParcelSize.SMALL, null)).isInstanceOf(EntityNotFoundException.class);
    }
}
