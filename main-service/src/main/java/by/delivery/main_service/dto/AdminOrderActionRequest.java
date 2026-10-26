package by.delivery.main_service.dto;

import jakarta.validation.constraints.NotNull;

public record AdminOrderActionRequest(@NotNull AdminOrderAction action, Integer courierId) {
}
