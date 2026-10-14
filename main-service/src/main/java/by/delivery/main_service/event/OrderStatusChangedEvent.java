package by.delivery.main_service.event;

import java.time.LocalDateTime;

import by.delivery.main_service.repository.entity.OrderStatus;

public record OrderStatusChangedEvent(Integer orderId,
        OrderStatus status,
        Integer userId,
        LocalDateTime createdAt,
        LocalDateTime assignedAt,
        LocalDateTime pickedUpAt,
        LocalDateTime deliveredAt) {
}
