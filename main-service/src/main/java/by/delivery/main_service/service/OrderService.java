package by.delivery.main_service.service;

import java.math.BigDecimal;
import java.util.List;

import by.delivery.main_service.repository.entity.ParcelSize;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.delivery.main_service.event.NewOrderEvent;
import by.delivery.main_service.repository.ClientRepository;
import by.delivery.main_service.repository.CourierZoneRepository;
import by.delivery.main_service.repository.OrderRepository;
import by.delivery.main_service.repository.ZoneRepository;
import by.delivery.main_service.repository.entity.Client;
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
    private final ZoneRepository zoneRepository;
    private final CourierZoneRepository courierZoneRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Order createOrder(Integer userId, Integer zoneId, String pickupAddress, String deliveryAddress,
                             BigDecimal weightKg, ParcelSize size, String description) {

        if (zoneId == null || isBlank(pickupAddress) || isBlank(deliveryAddress) || size == null
                || weightKg == null || weightKg.signum() <= 0)
            throw new IllegalArgumentException("Некорректные данные заказа");

        Client client = clientRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Профиль клиента не найден: " + userId));
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new EntityNotFoundException("Зона не найдена: " + zoneId));

        Order order = new Order();
        order.setClient(client);
        order.setZone(zone);
        order.setPickupAddress(pickupAddress);
        order.setDeliveryAddress(deliveryAddress);
        order.setStatus(OrderStatus.SEARCHING_COURIER);

        Parcel parcel = new Parcel();
        parcel.setOrder(order);
        parcel.setWeightKg(weightKg);
        parcel.setSize(size);
        parcel.setDescription(description);
        order.setParcel(parcel);

        Order saved = orderRepository.save(order);
        notifyCouriersOfZone(saved, zone);
        eventPublisher.publishEvent(NewOrderEvent.from(saved));
        return saved;
    }

    private void notifyCouriersOfZone(Order order, Zone zone) {
        List<Integer> courierUserIds = courierZoneRepository.findByZone_Id(zone.getId()).stream()
                .map(link -> link.getCourier().getUserId())
                .toList();
        notificationService.notifyUsers(courierUserIds, NotificationType.NEW_ORDER_AVAILABLE,
                "Новая заявка #" + order.getId() + " (" + zone.getName() + "): "
                        + order.getPickupAddress() + " → " + order.getDeliveryAddress());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

