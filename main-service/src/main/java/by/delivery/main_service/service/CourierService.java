package by.delivery.main_service.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import by.delivery.main_service.event.OrderStatusChangedEvent;
import by.delivery.main_service.repository.CourierRepository;
import by.delivery.main_service.repository.OrderRepository;
import by.delivery.main_service.repository.entity.Courier;
import by.delivery.main_service.repository.entity.Order;
import by.delivery.main_service.repository.entity.OrderStatus;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CourierService {
    private final OrderRepository orderRepository;
    private final CourierRepository courierRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void changeStatusByCourier(
            Integer orderId,
            OrderStatus newStatus,
            Integer userId) {
        validateTargetStatus(newStatus);

        Order order = getOrderForUpdate(orderId);
        Courier courier = getCourier(userId);

        ensureCourierAssigned(order);

        if (!Objects.equals(
                courier.getId(),
                order.getCourier().getId())) {
            throw new SecurityException(
                    "Order is assigned to another courier");
        }

        applyStatusChange(order, newStatus);
    }

    public void changeStatusByAdmin(
            Integer orderId,
            OrderStatus newStatus) {
        validateTargetStatus(newStatus);

        Order order = getOrderForUpdate(orderId);

        ensureCourierAssigned(order);

        applyStatusChange(order, newStatus);
    }

    private Order getOrderForUpdate(Integer orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order not found: " + orderId));
    }

    private Courier getCourier(Integer userId) {
        return courierRepository
                .findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Courier profile not found for user: "
                                + userId));
    }

    private void ensureCourierAssigned(Order order) {
        if (order.getCourier() == null) {
            throw new IllegalStateException(
                    "Cannot change order status: no courier assigned");
        }
    }

    private void validateTargetStatus(OrderStatus newStatus) {
        if (newStatus != OrderStatus.PICKED_UP
                && newStatus != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException(
                    "Only PICKED_UP and DELIVERED are supported");
        }
    }

    private void applyStatusChange(
            Order order,
            OrderStatus newStatus) {
        LocalDateTime now = LocalDateTime.now()
                .truncatedTo(ChronoUnit.MICROS);

        switch (newStatus) {
            case PICKED_UP -> {
                if (order.getStatus() != OrderStatus.COURIER_ASSIGNED) {
                    throw new IllegalStateException(
                            "Order must be COURIER_ASSIGNED "
                                    + "before PICKED_UP");
                }

                order.setPickedUpAt(now);
            }

            case DELIVERED -> {
                if (order.getStatus() != OrderStatus.PICKED_UP
                        || order.getPickedUpAt() == null) {
                    throw new IllegalStateException(
                            "Order must be PICKED_UP before DELIVERED");
                }

                order.setDeliveredAt(now);
            }

            default -> throw new IllegalArgumentException(
                    "Unsupported target status: " + newStatus);
        }

        order.setStatus(newStatus);

        orderRepository.save(order);

        OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                order.getId(),
                order.getStatus(),
                order.getClient().getUserId(),
                order.getCreatedAt(),
                order.getAssignedAt(),
                order.getPickedUpAt(),
                order.getDeliveredAt());

        eventPublisher.publishEvent(event);
    }

}
