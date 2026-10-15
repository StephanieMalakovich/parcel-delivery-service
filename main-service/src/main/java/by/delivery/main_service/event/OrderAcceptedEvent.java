package by.delivery.main_service.event;

import java.time.LocalDateTime;
import java.util.UUID;

import by.delivery.main_service.repository.entity.Order;
import by.delivery.main_service.repository.entity.OrderStatus;


public record OrderAcceptedEvent(
        UUID eventId,
        String type,
        LocalDateTime occurredAt,
        Integer orderId,
        Integer zoneId,
        Integer clientUserId,
        Integer courierId,
        Integer courierUserId,
        String courierName,
        OrderStatus status,
        LocalDateTime assignedAt) {

    public static final String TYPE = "ORDER_ACCEPTED";

    public static OrderAcceptedEvent from(Order order) {
        return new OrderAcceptedEvent(
                UUID.randomUUID(),
                TYPE,
                LocalDateTime.now(),
                order.getId(),
                order.getZone().getId(),
                order.getClient().getUserId(),
                order.getCourier().getId(),
                order.getCourier().getUserId(),
                order.getCourier().getFullName(),
                order.getStatus(),
                order.getAssignedAt());
    }
}
