package by.delivery.main_service.dto;

import java.math.BigDecimal;

import by.delivery.main_service.repository.entity.ParcelSize;

public record ParcelRequest(BigDecimal weightKg, ParcelSize size, String description) {
}
