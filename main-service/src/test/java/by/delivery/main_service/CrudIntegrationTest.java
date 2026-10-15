package by.delivery.main_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import by.delivery.main_service.repository.ClientRepository;
import by.delivery.main_service.repository.CourierRepository;
import by.delivery.main_service.repository.CourierZoneRepository;
import by.delivery.main_service.repository.OrderRepository;
import by.delivery.main_service.repository.ParcelRepository;
import by.delivery.main_service.repository.ReviewRepository;
import by.delivery.main_service.repository.UserNotificationRepository;
import by.delivery.main_service.repository.ZoneRepository;
import by.delivery.main_service.repository.entity.Client;
import by.delivery.main_service.repository.entity.Courier;
import by.delivery.main_service.repository.entity.CourierStatus;
import by.delivery.main_service.repository.entity.CourierZone;
import by.delivery.main_service.repository.entity.NotificationType;
import by.delivery.main_service.repository.entity.Order;
import by.delivery.main_service.repository.entity.OrderStatus;
import by.delivery.main_service.repository.entity.Parcel;
import by.delivery.main_service.repository.entity.ParcelSize;
import by.delivery.main_service.repository.entity.Review;
import by.delivery.main_service.repository.entity.UserNotification;
import by.delivery.main_service.repository.entity.Zone;

@SpringBootTest(properties = "spring.liquibase.contexts=test")
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class CrudIntegrationTest {

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
    private ZoneRepository zoneRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private CourierRepository courierRepository;

    @Autowired
    private CourierZoneRepository courierZoneRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ParcelRepository parcelRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private UserNotificationRepository notificationRepository;

    @Autowired
    private TransactionTemplate tx;

    @Test
    void givenNewZone_whenCrudOperations_thenZoneIsCreatedUpdatedAndDeleted() {

        // Given
        Zone zone = new Zone();
        zone.setName("Центральный");

        // When: создание
        Zone saved = zoneRepository.saveAndFlush(zone);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(zoneRepository.findByName("Центральный")).isPresent();

        // When: обновление
        saved.setName("Северный");
        zoneRepository.saveAndFlush(saved);

        // Then
        assertThat(zoneRepository.findById(saved.getId()).orElseThrow().getName()).isEqualTo("Северный");

        // When: удаление
        zoneRepository.deleteById(saved.getId());

        // Then
        assertThat(zoneRepository.existsById(saved.getId())).isFalse();
    }

    @Test
    void givenDuplicateZoneName_whenSave_thenUniqueConstraintViolated() {

        // Given
        zoneRepository.saveAndFlush(newZone("Дубликат"));

        // When / Then
        assertThatThrownBy(() -> zoneRepository.saveAndFlush(newZone("Дубликат")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void givenNewClient_whenCrudOperations_thenClientIsCreatedUpdatedAndDeleted() {

        // Given
        Client client = newClient(1001);

        // When: создание
        Client saved = clientRepository.saveAndFlush(client);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(clientRepository.findByUserId(1001)).isPresent();
        assertThat(clientRepository.existsByUserId(1001)).isTrue();

        // When: обновление
        saved.setPhone("79991112233");
        clientRepository.saveAndFlush(saved);

        // Then
        assertThat(clientRepository.findById(saved.getId()).orElseThrow().getPhone()).isEqualTo("79991112233");

        // When: удаление
        clientRepository.deleteById(saved.getId());

        // Then
        assertThat(clientRepository.findByUserId(1001)).isEmpty();
    }

    @Test
    void givenCourierAndZone_whenLinked_thenCourierZoneIsCreatedAndDeleted() {

        // Given
        Courier courier = courierRepository.saveAndFlush(newCourier(2001));
        Zone zone = zoneRepository.saveAndFlush(newZone("Советский"));

        courierZoneRepository.saveAndFlush(new CourierZone(courier, zone));

        // Then
        assertThat(courierZoneRepository.existsByCourier_IdAndZone_Id(courier.getId(), zone.getId())).isTrue();
        assertThat(courierZoneRepository.findByZone_Id(zone.getId())).hasSize(1);
        assertThat(courierZoneRepository.findByCourier_Id(courier.getId())).hasSize(1);

        // When: удаление связи
        tx.executeWithoutResult(status ->
                courierZoneRepository.deleteByCourier_IdAndZone_Id(courier.getId(), zone.getId()));

        // Then
        assertThat(courierZoneRepository.existsByCourier_IdAndZone_Id(courier.getId(), zone.getId())).isFalse();
    }

    @Test
    void givenNewCourier_whenUpdateStatus_thenStatusIsSavedAndFilterWorks() {

        // Given
        Courier courier = courierRepository.saveAndFlush(newCourier(2001));
        assertThat(courierRepository.findByStatus(CourierStatus.AVAILABLE)).hasSize(1);

        // When
        courier.setStatus(CourierStatus.BUSY);
        courierRepository.saveAndFlush(courier);

        // Then
        assertThat(courierRepository.findByStatus(CourierStatus.AVAILABLE)).isEmpty();
        assertThat(courierRepository.findByStatus(CourierStatus.BUSY)).hasSize(1);
    }

    @Test
    void givenOrderWithParcel_whenCrudOperations_thenParcelFollowsOrder() {

        // Given
        Client client = clientRepository.saveAndFlush(newClient(1001));
        Zone zone = zoneRepository.saveAndFlush(newZone("Центральный"));

        Order order = new Order();
        order.setClient(client);
        order.setZone(zone);
        order.setPickupAddress("ул. Ленина 1");
        order.setDeliveryAddress("ул. Мира 10");

        Parcel parcel = new Parcel();
        parcel.setOrder(order);
        parcel.setWeightKg(new BigDecimal("1.50"));
        parcel.setSize(ParcelSize.MEDIUM);
        order.setParcel(parcel);

        // When: создание (посылка сохраняется каскадом)
        Order saved = orderRepository.saveAndFlush(order);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.SEARCHING_COURIER);
        assertThat(parcelRepository.findByOrder_Id(saved.getId())).isPresent();
        assertThat(orderRepository.findByClient_Id(client.getId(), PageRequest.of(0, 10)).getContent()).hasSize(1);
        assertThat(orderRepository.findByZone_IdAndStatusAndCourierIsNull(zone.getId(), OrderStatus.SEARCHING_COURIER))
                .hasSize(1);

        // When: обновление
        saved.setDeliveryAddress("ул. Победы 5");
        orderRepository.saveAndFlush(saved);

        // Then
        assertThat(orderRepository.findById(saved.getId()).orElseThrow().getDeliveryAddress())
                .isEqualTo("ул. Победы 5");

        // When: удаление заказа
        Integer orderId = saved.getId();
        tx.executeWithoutResult(status -> orderRepository.deleteById(orderId));

        // Then: посылка удалена вместе с заказом
        assertThat(orderRepository.existsById(orderId)).isFalse();
        assertThat(parcelRepository.existsByOrder_Id(orderId)).isFalse();
    }

    @Test
    void givenDeliveredOrder_whenReviewSaved_thenReviewIsReadableAndUnique() {

        // Given
        Client client = clientRepository.saveAndFlush(newClient(1001));
        Courier courier = courierRepository.saveAndFlush(newCourier(2001));
        Zone zone = zoneRepository.saveAndFlush(newZone("Центральный"));

        Order order = new Order();
        order.setClient(client);
        order.setCourier(courier);
        order.setZone(zone);
        order.setPickupAddress("A");
        order.setDeliveryAddress("B");
        order.setStatus(OrderStatus.DELIVERED);
        Order savedOrder = orderRepository.saveAndFlush(order);

        Review review = new Review();
        review.setOrder(savedOrder);
        review.setCourier(courier);
        review.setRating((short) 5);
        review.setComment("Быстро");

        // When
        Review saved = reviewRepository.saveAndFlush(review);

        // Then
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(reviewRepository.findByOrder_Id(savedOrder.getId())).isPresent();
        assertThat(reviewRepository.findByCourier_Id(courier.getId(), PageRequest.of(0, 10)).getContent())
                .hasSize(1);

        // When / Then: второй отзыв на тот же заказ запрещён ограничением уникальности
        Review duplicate = new Review();
        duplicate.setOrder(savedOrder);
        duplicate.setCourier(courier);
        duplicate.setRating((short) 4);
        assertThatThrownBy(() -> reviewRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void givenNotifications_whenMarkedRead_thenUnreadCounterDecreases() {

        // Given
        notificationRepository.saveAndFlush(newNotification(1001, "Первое"));
        UserNotification second = notificationRepository.saveAndFlush(newNotification(1001, "Второе"));
        notificationRepository.saveAndFlush(newNotification(1002, "Чужое"));

        // Then
        assertThat(notificationRepository.countByUserIdAndIsReadFalse(1001)).isEqualTo(2);

        // When: обновление (прочитано)
        second.setIsRead(true);
        notificationRepository.saveAndFlush(second);

        // Then
        assertThat(notificationRepository.countByUserIdAndIsReadFalse(1001)).isEqualTo(1);
        assertThat(notificationRepository
                .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(1001, PageRequest.of(0, 10)).getContent())
                .hasSize(1);

        // When: удаление
        notificationRepository.deleteById(second.getId());

        // Then
        assertThat(notificationRepository
                .findByUserIdOrderByCreatedAtDesc(1001, PageRequest.of(0, 10)).getContent())
                .hasSize(1);
    }

    private Zone newZone(String name) {
        Zone zone = new Zone();
        zone.setName(name);
        return zone;
    }

    private Client newClient(Integer userId) {
        Client client = new Client();
        client.setUserId(userId);
        client.setFullName("Тестовый клиент " + userId);
        return client;
    }

    private Courier newCourier(Integer userId) {
        Courier courier = new Courier();
        courier.setUserId(userId);
        courier.setFullName("Тестовый курьер " + userId);
        return courier;
    }

    private UserNotification newNotification(Integer userId, String text) {
        UserNotification notification = new UserNotification();
        notification.setUserId(userId);
        notification.setType(NotificationType.ORDER_STATUS_CHANGED);
        notification.setText(text);
        return notification;
    }
}
