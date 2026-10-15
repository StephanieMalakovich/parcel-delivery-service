package by.delivery.main_service.event;

import java.time.LocalDateTime;
import java.util.UUID;

import by.delivery.main_service.repository.entity.CancelledBy;
import by.delivery.main_service.repository.entity.Order;
import by.delivery.main_service.repository.entity.OrderStatus;


public record OrderCancelledEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        Integer orderId,
        Integer courierId,
        OrderStatus status,
        CancelledBy cancelledBy,
        LocalDateTime createdAt,
        LocalDateTime assignedAt,
        LocalDateTime pickedUpAt,
        LocalDateTime cancelledAt) {

    public static OrderCancelledEvent from(Order o) {
        return new OrderCancelledEvent(UUID.randomUUID(), LocalDateTime.now(), o.getId(),
                o.getCourier() == null ? null : o.getCourier().getId(),
                o.getStatus(), o.getCancelledBy(), o.getCreatedAt(), o.getAssignedAt(), o.getPickedUpAt(), o.getCancelledAt());
    }
}
