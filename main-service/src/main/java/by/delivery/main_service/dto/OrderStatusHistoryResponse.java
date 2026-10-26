package by.delivery.main_service.dto;

import java.time.LocalDateTime;

import by.delivery.main_service.repository.entity.OrderStatus;
import by.delivery.main_service.repository.entity.OrderStatusHistory;

public record OrderStatusHistoryResponse(OrderStatus status, LocalDateTime changedAt) {

    public static OrderStatusHistoryResponse from(OrderStatusHistory entry) {
        return new OrderStatusHistoryResponse(entry.getStatus(), entry.getChangedAt());
    }
}
