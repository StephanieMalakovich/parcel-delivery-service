package by.delivery.main_service.dto;

import by.delivery.main_service.repository.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(@NotNull OrderStatus status) {
}
