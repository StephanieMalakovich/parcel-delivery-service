package by.delivery.main_service.service;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.delivery.main_service.event.OrderStatusChangedEvent;
import by.delivery.main_service.repository.CourierRepository;
import by.delivery.main_service.repository.OrderRepository;
import by.delivery.main_service.repository.entity.Courier;
import by.delivery.main_service.repository.entity.CourierStatus;
import by.delivery.main_service.repository.entity.Order;
import by.delivery.main_service.repository.entity.OrderStatus;

import jakarta.persistence.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CourierService {

    private final OrderRepository orderRepository;
    private final CourierRepository courierRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void changeStatusByCourier(Integer orderId, OrderStatus newStatus, Integer courierUserId) {
        validateTargetStatus(newStatus);

        Order order = loadOrderForUpdate(orderId);
        Courier courier = loadCourier(courierUserId);

        ensureCourierAssigned(order);
        if (!Objects.equals(courier.getId(), order.getCourier().getId())) {
            throw new SecurityException("Заказ назначен другому курьеру");
        }

        applyStatusChange(order, newStatus);
    }

    @Transactional
    public void changeStatusByAdmin(Integer orderId, OrderStatus newStatus) {
        validateTargetStatus(newStatus);

        Order order = loadOrderForUpdate(orderId);

        ensureCourierAssigned(order);
        applyStatusChange(order, newStatus);
    }

    private void applyStatusChange(Order order, OrderStatus newStatus) {
        LocalDateTime now = LocalDateTime.now();

        switch (newStatus) {
            case PICKED_UP -> markPickedUp(order, now);
            case DELIVERED -> markDelivered(order, now);
            default -> throw new IllegalArgumentException("Неподдерживаемый статус: " + newStatus);
        }

        order.setStatus(newStatus);
        eventPublisher.publishEvent(OrderStatusChangedEvent.from(order));
    }

    private void markPickedUp(Order order, LocalDateTime now) {
        if (order.getStatus() != OrderStatus.COURIER_ASSIGNED) {
            throw new IllegalStateException("Перед статусом PICKED_UP заказ должен быть в статусе COURIER_ASSIGNED");
        }
        order.setPickedUpAt(now);
    }

    private void markDelivered(Order order, LocalDateTime now) {
        if (order.getStatus() != OrderStatus.PICKED_UP || order.getPickedUpAt() == null) {
            throw new IllegalStateException("Перед статусом DELIVERED заказ должен быть в статусе PICKED_UP");
        }
        order.setDeliveredAt(now);
        order.getCourier().setStatus(CourierStatus.AVAILABLE);
    }

    private Order loadOrderForUpdate(Integer orderId) {
        return orderRepository.findWithLockById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Заказ не найден: " + orderId));
    }

    private Courier loadCourier(Integer courierUserId) {
        return courierRepository.findByUserId(courierUserId)
                .orElseThrow(() -> new EntityNotFoundException("Профиль курьера не найден: " + courierUserId));
    }

    private void ensureCourierAssigned(Order order) {
        if (order.getCourier() == null) {
            throw new IllegalStateException("Нельзя изменить статус: курьер не назначен");
        }
    }

    private void validateTargetStatus(OrderStatus newStatus) {
        if (newStatus != OrderStatus.PICKED_UP && newStatus != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Поддерживаются только статусы PICKED_UP и DELIVERED");
        }
    }
}
