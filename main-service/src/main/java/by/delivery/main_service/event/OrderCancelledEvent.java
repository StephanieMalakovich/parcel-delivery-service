package by.delivery.main_service.event;

import java.time.LocalDateTime;
import java.util.UUID;

import by.delivery.main_service.repository.entity.CancelledBy;
import by.delivery.main_service.repository.entity.Order;
import by.delivery.main_service.repository.entity.OrderStatus;

public record OrderCancelledEvent(
        UUID eventId,
        String type,
        LocalDateTime occurredAt,
        Integer orderId,
        Integer courierId,
        OrderStatus status,
        CancelledBy cancelledBy,
        LocalDateTime createdAt,
        LocalDateTime assignedAt,
        LocalDateTime pickedUpAt,
        LocalDateTime cancelledAt) {

    public static final String TYPE = "ORDER_CANCELLED";

    public static OrderCancelledEvent from(Order order) {
        return new OrderCancelledEvent(
                UUID.randomUUID(),
                TYPE,
                LocalDateTime.now(),
                order.getId(),
                order.getCourier() == null ? null : order.getCourier().getId(),
                order.getStatus(),
                order.getCancelledBy(),
                order.getCreatedAt(),
                order.getAssignedAt(),
                order.getPickedUpAt(),
                order.getCancelledAt());
    }
}
