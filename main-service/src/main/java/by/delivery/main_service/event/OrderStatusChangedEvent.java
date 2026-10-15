package by.delivery.main_service.event;

import java.time.LocalDateTime;
import java.util.UUID;

import by.delivery.main_service.repository.entity.CancelledBy;
import by.delivery.main_service.repository.entity.Order;
import by.delivery.main_service.repository.entity.OrderStatus;

public record OrderStatusChangedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        Integer orderId,
        OrderStatus status,
        CancelledBy cancelledBy,
        Integer zoneId,
        Integer clientUserId,
        Integer previousCourierUserId,
        Integer courierUserId,
        LocalDateTime createdAt,
        LocalDateTime assignedAt,
        LocalDateTime pickedUpAt,
        LocalDateTime deliveredAt,
        LocalDateTime cancelledAt) {

    public static OrderStatusChangedEvent from(Order o) {
        return of(o, null, o.getCourier() == null ? null : o.getCourier().getUserId());
    }

    public static OrderStatusChangedEvent of(Order o, Integer previousCourierUserId, Integer courierUserId) {
        return new OrderStatusChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
                o.getId(), o.getStatus(), o.getCancelledBy(), o.getZone().getId(),
                o.getClient().getUserId(), previousCourierUserId, courierUserId,
                o.getCreatedAt(), o.getAssignedAt(), o.getPickedUpAt(), o.getDeliveredAt(), o.getCancelledAt());
    }
}
