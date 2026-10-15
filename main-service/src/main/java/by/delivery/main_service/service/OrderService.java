package by.delivery.main_service.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.delivery.main_service.dto.CreateOrderRequest;
import by.delivery.main_service.dto.OrderResponse;
import by.delivery.main_service.event.NewOrderEvent;
import by.delivery.main_service.event.OrderAcceptedEvent;
import by.delivery.main_service.event.OrderCancelledEvent;
import by.delivery.main_service.event.OrderStatusChangedEvent;
import by.delivery.main_service.repository.ClientRepository;
import by.delivery.main_service.repository.CourierRepository;
import by.delivery.main_service.repository.CourierZoneRepository;
import by.delivery.main_service.repository.OrderRepository;
import by.delivery.main_service.repository.ZoneRepository;
import by.delivery.main_service.repository.entity.CancellBy;
import by.delivery.main_service.repository.entity.Client;
import by.delivery.main_service.repository.entity.Courier;
import by.delivery.main_service.repository.entity.CourierStatus;
import by.delivery.main_service.repository.entity.NotificationType;
import by.delivery.main_service.repository.entity.Order;
import by.delivery.main_service.repository.entity.OrderStatus;
import by.delivery.main_service.repository.entity.Parcel;
import by.delivery.main_service.repository.entity.Zone;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;
    private final CourierRepository courierRepository;
    private final ZoneRepository zoneRepository;
    private final CourierZoneRepository courierZoneRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;


    @Transactional
    public OrderResponse createOrder(Integer userId, CreateOrderRequest request) {
        validate(request);

        Client client = clientRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Профиль клиента не найден: " + userId));
        Zone zone = zoneRepository.findById(request.zoneId())
                .orElseThrow(() -> new EntityNotFoundException("Зона не найдена: " + request.zoneId()));

        Order order = new Order();
        order.setClient(client);
        order.setZone(zone);
        order.setPickupAddress(request.pickupAddress());
        order.setDeliveryAddress(request.deliveryAddress());
        order.setStatus(OrderStatus.SEARCHING_COURIER);

        Parcel parcel = new Parcel();
        parcel.setOrder(order);
        parcel.setWeightKg(request.parcel().weightKg());
        parcel.setSize(request.parcel().size());
        parcel.setDescription(request.parcel().description());
        order.setParcel(parcel);

        Order saved = orderRepository.save(order);
        notifyCouriersOfZone(saved, zone);
        eventPublisher.publishEvent(NewOrderEvent.from(saved));
        return OrderResponse.from(saved);
    }


    @Transactional
    public OrderResponse acceptOrder(Integer courierUserId, Integer orderId) {
        Courier courier = courierRepository.findByUserId(courierUserId)
                .orElseThrow(() -> new EntityNotFoundException("Профиль курьера не найден: " + courierUserId));
        Order order = orderRepository.findWithLockById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Заказ не найден: " + orderId));

        if (order.getStatus() != OrderStatus.SEARCHING_COURIER) {
            throw new IllegalStateException("Заявка #" + orderId + " уже назначена или недоступна");
        }
        if (courier.getStatus() == CourierStatus.BUSY) {
            throw new IllegalStateException("Курьер занят");
        }
        if (!courierZoneRepository.existsByCourier_IdAndZone_Id(courier.getId(), order.getZone().getId())) {
            throw new IllegalStateException("Курьер не обслуживает зону заказа");
        }

        order.setCourier(courier);
        order.setStatus(OrderStatus.COURIER_ASSIGNED);
        order.setAssignedAt(LocalDateTime.now());
        courier.setStatus(CourierStatus.BUSY);

        notificationService.notifyUsers(List.of(order.getClient().getUserId()), NotificationType.ORDER_ASSIGNED,
                "По заказу #" + orderId + " назначен курьер " + courier.getFullName());
        eventPublisher.publishEvent(OrderAcceptedEvent.from(order));
        return OrderResponse.from(order);
    }


    @Transactional
    public OrderResponse reassignOrderByAdmin(Integer orderId, Integer newCourierId) {
        Order order = loadNotFinished(orderId);
        Courier next = courierRepository.findById(newCourierId)
                .orElseThrow(() -> new EntityNotFoundException("Курьер не найден: " + newCourierId));
        Courier previous = order.getCourier();

        if (previous != null && previous.getId().equals(newCourierId)) {
            throw new IllegalStateException("Этот курьер уже назначен на заказ");
        }
        if (next.getStatus() == CourierStatus.BUSY) {
            throw new IllegalStateException("Новый курьер занят");
        }
        if (!courierZoneRepository.existsByCourier_IdAndZone_Id(next.getId(), order.getZone().getId())) {
            throw new IllegalStateException("Новый курьер не обслуживает зону заказа");
        }

        Integer previousUserId = previous == null ? null : previous.getUserId();

        order.setCourier(next);
        next.setStatus(CourierStatus.BUSY);
        if (order.getStatus() == OrderStatus.SEARCHING_COURIER) {
            order.setStatus(OrderStatus.COURIER_ASSIGNED);
        }
        if (order.getStatus() == OrderStatus.COURIER_ASSIGNED) {
            order.setAssignedAt(LocalDateTime.now());
        }

        if (previous != null) {
            previous.setStatus(CourierStatus.AVAILABLE);
            notificationService.notifyUsers(List.of(previous.getUserId()), NotificationType.ORDER_CANCELLED,
                    "Заказ #" + orderId + " снят администратором");
        }
        notificationService.notifyUsers(List.of(next.getUserId()), NotificationType.ORDER_ASSIGNED,
                "Вам назначен заказ #" + orderId);
        notificationService.notifyUsers(List.of(order.getClient().getUserId()), NotificationType.ORDER_STATUS_CHANGED,
                "По заказу #" + orderId + " изменён курьер: " + next.getFullName());

        eventPublisher.publishEvent(OrderStatusChangedEvent.of(order, previousUserId, next.getUserId()));
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancelOrderByAdmin(Integer orderId) {
        Order order = loadNotFinished(orderId);
        Courier courier = order.getCourier();

        order.setStatus(OrderStatus.CANCELLED);
        order.setRole(CancellBy.ADMIN);
        order.setCancelledAt(LocalDateTime.now());

        Integer courierUserId = null;
        if (courier != null) {
            courierUserId = courier.getUserId();
            courier.setStatus(CourierStatus.AVAILABLE);
            notificationService.notifyUsers(List.of(courierUserId), NotificationType.ORDER_CANCELLED,
                    "Заказ #" + orderId + " отменён администратором");
        }
        notificationService.notifyUsers(List.of(order.getClient().getUserId()), NotificationType.ORDER_STATUS_CHANGED,
                "Заказ #" + orderId + " отменён администратором");

        eventPublisher.publishEvent(OrderStatusChangedEvent.of(order, courierUserId, null));
        eventPublisher.publishEvent(OrderCancelledEvent.from(order));
        return OrderResponse.from(order);
    }


    private Order loadNotFinished(Integer orderId) {
        Order order = orderRepository.findWithLockById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Заказ не найден: " + orderId));
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Заказ уже завершён: " + order.getStatus());
        }
        return order;
    }

    private void notifyCouriersOfZone(Order order, Zone zone) {
        List<Integer> courierUserIds = courierZoneRepository.findByZone_Id(zone.getId()).stream()
                .map(link -> link.getCourier().getUserId())
                .toList();
        notificationService.notifyUsers(courierUserIds, NotificationType.NEW_ORDER_AVAILABLE,
                "Новая заявка #" + order.getId() + " (" + zone.getName() + "): "
                        + order.getPickupAddress() + " → " + order.getDeliveryAddress());
    }

    private static void validate(CreateOrderRequest r) {
        if (r == null || r.zoneId() == null) {
            throw new IllegalArgumentException("Не указана зона");
        }
        if (isBlank(r.pickupAddress()) || isBlank(r.deliveryAddress())) {
            throw new IllegalArgumentException("Адреса отправления и получения обязательны");
        }
        if (r.parcel() == null || r.parcel().size() == null) {
            throw new IllegalArgumentException("Не указаны параметры посылки");
        }
        if (r.parcel().weightKg() == null || r.parcel().weightKg().signum() <= 0) {
            throw new IllegalArgumentException("Вес посылки должен быть больше 0");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}