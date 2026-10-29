package by.delivery.main_service.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.delivery.main_service.dto.CreateOrderRequest;
import by.delivery.main_service.dto.OrderResponse;
import by.delivery.main_service.dto.PageResponse;
import by.delivery.main_service.event.NewOrderEvent;
import by.delivery.main_service.event.OrderAcceptedEvent;
import by.delivery.main_service.event.OrderCancelledEvent;
import by.delivery.main_service.event.OrderStatusChangedEvent;
import by.delivery.main_service.repository.ClientRepository;
import by.delivery.main_service.repository.CourierRepository;
import by.delivery.main_service.repository.CourierZoneRepository;
import by.delivery.main_service.repository.OrderRepository;
import by.delivery.main_service.repository.ZoneRepository;
import by.delivery.main_service.repository.entity.CancelledBy;
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

@Slf4j
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
        order.changeStatus(OrderStatus.SEARCHING_COURIER, LocalDateTime.now());

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
        Order order = loadOrderForUpdate(orderId);

        if (order.getStatus() != OrderStatus.SEARCHING_COURIER) {
            throw new IllegalStateException("Заявка #" + orderId + " уже назначена или недоступна");
        }
        if (courier.getStatus() == CourierStatus.BUSY) {
            throw new IllegalStateException("Курьер занят");
        }
        if (!servesZone(courier, order)) {
            throw new IllegalStateException("Курьер не обслуживает зону заказа");
        }

        order.setCourier(courier);
        order.changeStatus(OrderStatus.COURIER_ASSIGNED, LocalDateTime.now());
        courier.setStatus(CourierStatus.BUSY);

        notificationService.notifyUser(order.getClient().getUserId(), NotificationType.ORDER_ASSIGNED,
                "По заказу #" + orderId + " назначен курьер " + courier.getFullName());
        eventPublisher.publishEvent(OrderAcceptedEvent.from(order));
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancelOrderByClient(Integer userId, Integer orderId) {
        if (userId == null || orderId == null) {
            throw new IllegalArgumentException("Идентификаторы пользователя и заказа обязательны");
        }

        Order order = loadOrderForUpdate(orderId);

        if (!Objects.equals(order.getClient().getUserId(), userId)) {
            throw new SecurityException("Заказ не принадлежит этому клиенту");
        }
        if (order.getStatus() != OrderStatus.SEARCHING_COURIER
                && order.getStatus() != OrderStatus.COURIER_ASSIGNED) {
            throw new IllegalStateException("Нельзя отменить заказ в статусе " + order.getStatus());
        }

        cancel(order, CancelledBy.CLIENT);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse reassignOrderByAdmin(Integer orderId, Integer newCourierId) {
        Order order = loadNotFinishedOrder(orderId);
        Courier next = courierRepository.findById(newCourierId)
                .orElseThrow(() -> new EntityNotFoundException("Курьер не найден: " + newCourierId));
        Courier previous = order.getCourier();

        if (previous != null && previous.getId().equals(newCourierId)) {
            throw new IllegalStateException("Этот курьер уже назначен на заказ");
        }
        if (next.getStatus() == CourierStatus.BUSY) {
            throw new IllegalStateException("Новый курьер занят");
        }
        if (!servesZone(next, order)) {
            throw new IllegalStateException("Новый курьер не обслуживает зону заказа");
        }

        Integer previousUserId = previous == null ? null : previous.getUserId();

        order.setCourier(next);
        next.setStatus(CourierStatus.BUSY);
        if (order.getStatus() == OrderStatus.SEARCHING_COURIER) {
            order.changeStatus(OrderStatus.COURIER_ASSIGNED, LocalDateTime.now());
        } else if (order.getStatus() == OrderStatus.COURIER_ASSIGNED) {
            order.addStatusHistory(OrderStatus.COURIER_ASSIGNED, LocalDateTime.now());
        }

        if (previous != null) {
            previous.setStatus(CourierStatus.AVAILABLE);
            notificationService.notifyUser(previous.getUserId(), NotificationType.ORDER_CANCELLED,
                    "Заказ #" + orderId + " снят администратором");
        }
        notificationService.notifyUser(next.getUserId(), NotificationType.ORDER_ASSIGNED,
                "Вам назначен заказ #" + orderId);
        notificationService.notifyUser(order.getClient().getUserId(), NotificationType.ORDER_STATUS_CHANGED,
                "По заказу #" + orderId + " изменён курьер: " + next.getFullName());

        eventPublisher.publishEvent(OrderStatusChangedEvent.of(order, previousUserId, next.getUserId()));
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancelOrderByAdmin(Integer orderId) {
        Order order = loadNotFinishedOrder(orderId);

        cancel(order, CancelledBy.ADMIN);
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Integer orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Заказ не найден: " + orderId));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderForUser(Integer orderId, Integer userId, boolean admin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Заказ не найден: " + orderId));

        boolean isClientOfOrder = Objects.equals(order.getClient().getUserId(), userId);
        boolean isCourierOfOrder = order.getCourier() != null
                && Objects.equals(order.getCourier().getUserId(), userId);
        if (!admin && !isClientOfOrder && !isCourierOfOrder) {
            throw new SecurityException("Нет доступа к этому заказу");
        }
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getClientOrders(Integer userId, Pageable pageable) {
        Client client = clientRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Профиль клиента не найден: " + userId));
        return toPage(orderRepository.findByClient_Id(client.getId(), pageable));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getCourierOrders(Integer courierUserId, OrderStatus status, Pageable pageable) {
        Courier courier = courierRepository.findByUserId(courierUserId)
                .orElseThrow(() -> new EntityNotFoundException("Профиль курьера не найден: " + courierUserId));
        if (status == null) {
            return toPage(orderRepository.findByCourier_Id(courier.getId(), pageable));
        }
        return toPage(orderRepository.findByCourier_IdAndStatus(courier.getId(), status, pageable));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAvailableOrders(Integer courierUserId, Pageable pageable) {
        Courier courier = courierRepository.findByUserId(courierUserId)
                .orElseThrow(() -> new EntityNotFoundException("Профиль курьера не найден: " + courierUserId));
        List<Integer> zoneIds = courierZoneRepository.findByCourier_Id(courier.getId()).stream()
                .map(link -> link.getId().getZoneId())
                .toList();
        if (zoneIds.isEmpty()) {
            return PageResponse.of(Page.empty(pageable));
        }
        return toPage(orderRepository.findByStatusAndZone_IdIn(OrderStatus.SEARCHING_COURIER, zoneIds, pageable));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAllOrders(OrderStatus status, Pageable pageable) {
        if (status == null) {
            return toPage(orderRepository.findAll(pageable));
        }
        return toPage(orderRepository.findByStatus(status, pageable));
    }

    private PageResponse<OrderResponse> toPage(Page<Order> orders) {
        return PageResponse.of(orders.map(OrderResponse::from));
    }

    private void cancel(Order order, CancelledBy cancelledBy) {
        Courier courier = order.getCourier();
        String who = cancelledBy == CancelledBy.ADMIN ? "администратором" : "клиентом";

        order.changeStatus(OrderStatus.CANCELLED, LocalDateTime.now());
        order.setCancelledBy(cancelledBy);

        Integer courierUserId = null;
        if (courier != null) {
            courierUserId = courier.getUserId();
            courier.setStatus(CourierStatus.AVAILABLE);
            notificationService.notifyUser(courierUserId, NotificationType.ORDER_CANCELLED,
                    "Заказ #" + order.getId() + " отменён " + who);
        }
        if (cancelledBy == CancelledBy.ADMIN) {
            notificationService.notifyUser(order.getClient().getUserId(), NotificationType.ORDER_STATUS_CHANGED,
                    "Заказ #" + order.getId() + " отменён " + who);
        }

        eventPublisher.publishEvent(OrderStatusChangedEvent.of(order, courierUserId, null));
        eventPublisher.publishEvent(OrderCancelledEvent.from(order));
    }

    private Order loadOrderForUpdate(Integer orderId) {
        return orderRepository.findWithLockById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Заказ не найден: " + orderId));
    }

    private Order loadNotFinishedOrder(Integer orderId) {
        Order order = loadOrderForUpdate(orderId);
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Заказ уже завершён: " + order.getStatus());
        }
        return order;
    }

    private boolean servesZone(Courier courier, Order order) {
        return courierZoneRepository.existsByCourier_IdAndZone_Id(courier.getId(), order.getZone().getId());
    }

    private void notifyCouriersOfZone(Order order, Zone zone) {
        List<Integer> courierUserIds = courierZoneRepository.findByZone_Id(zone.getId()).stream()
                .map(link -> link.getCourier().getUserId())
                .toList();

        notificationService.notifyUsers(courierUserIds, NotificationType.NEW_ORDER_AVAILABLE,
                "Новая заявка #" + order.getId() + " (" + zone.getName() + "): "
                        + order.getPickupAddress() + " → " + order.getDeliveryAddress());
    }

    private static void validate(CreateOrderRequest request) {
        if (request == null || request.zoneId() == null) {
            throw new IllegalArgumentException("Не указана зона");
        }
        if (isBlank(request.pickupAddress()) || isBlank(request.deliveryAddress())) {
            throw new IllegalArgumentException("Адреса отправления и получения обязательны");
        }
        if (request.parcel() == null || request.parcel().size() == null) {
            throw new IllegalArgumentException("Не указаны параметры посылки");
        }
        if (request.parcel().weightKg() == null || request.parcel().weightKg().signum() <= 0) {
            throw new IllegalArgumentException("Вес посылки должен быть больше 0");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
