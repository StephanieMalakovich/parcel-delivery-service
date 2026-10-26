package by.delivery.main_service.event;

import java.time.LocalDateTime;
import java.util.UUID;

import by.delivery.main_service.repository.entity.CancelledBy;
import by.delivery.main_service.repository.entity.Order;
import by.delivery.main_service.repository.entity.OrderStatus;

public record OrderStatusChangedEvent(
        UUID eventId,
        String type,
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

    public static final String TYPE = "ORDER_STATUS_CHANGED";

    public static OrderStatusChangedEvent from(Order order) {
        Integer courierUserId = order.getCourier() == null ? null : order.getCourier().getUserId();
        return of(order, null, courierUserId);
    }

    public static OrderStatusChangedEvent of(Order order, Integer previousCourierUserId, Integer courierUserId) {
        return new OrderStatusChangedEvent(
                UUID.randomUUID(),
                TYPE,
                LocalDateTime.now(),
                order.getId(),
                order.getStatus(),
                order.getCancelledBy(),
                order.getZone().getId(),
                order.getClient().getUserId(),
                previousCourierUserId,
                courierUserId,
                order.getCreatedAt(),
                order.findStatusTime(OrderStatus.COURIER_ASSIGNED),
                order.findStatusTime(OrderStatus.PICKED_UP),
                order.findStatusTime(OrderStatus.DELIVERED),
                order.findStatusTime(OrderStatus.CANCELLED));
    }
}
