package by.delivery.main_service.service;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.delivery.main_service.dto.OrderResponse;
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
    public OrderResponse changeStatusByCourier(Integer orderId, OrderStatus newStatus, String courierUserId) {
        validateTargetStatus(newStatus);

        Order order = loadOrderForUpdate(orderId);
        Courier courier = loadCourier(courierUserId);

        ensureCourierAssigned(order);
        if (!Objects.equals(courier.getId(), order.getCourier().getId())) {
            throw new SecurityException("Заказ назначен другому курьеру");
        }

        applyStatusChange(order, newStatus);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse changeStatusByAdmin(Integer orderId, OrderStatus newStatus) {
        validateTargetStatus(newStatus);

        Order order = loadOrderForUpdate(orderId);

        ensureCourierAssigned(order);
        applyStatusChange(order, newStatus);
        return OrderResponse.from(order);
    }

    private void applyStatusChange(Order order, OrderStatus newStatus) {
        switch (newStatus) {
            case PICKED_UP -> ensureCanPickUp(order);
            case DELIVERED -> markDelivered(order);
            default -> throw new IllegalArgumentException("Неподдерживаемый статус: " + newStatus);
        }

        order.changeStatus(newStatus, LocalDateTime.now());
        eventPublisher.publishEvent(OrderStatusChangedEvent.from(order));
    }

    private void ensureCanPickUp(Order order) {
        if (order.getStatus() != OrderStatus.COURIER_ASSIGNED) {
            throw new IllegalStateException("Перед статусом PICKED_UP заказ должен быть в статусе COURIER_ASSIGNED");
        }
    }

    private void markDelivered(Order order) {
        if (order.getStatus() != OrderStatus.PICKED_UP || order.findStatusTime(OrderStatus.PICKED_UP) == null) {
            throw new IllegalStateException("Перед статусом DELIVERED заказ должен быть в статусе PICKED_UP");
        }
        order.getCourier().setStatus(CourierStatus.AVAILABLE);
    }

    private Order loadOrderForUpdate(Integer orderId) {
        return orderRepository.findWithLockById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Заказ не найден: " + orderId));
    }

    private Courier loadCourier(String courierUserId) {
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
