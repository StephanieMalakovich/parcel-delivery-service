package by.delivery.main_service.dto;

import java.math.BigDecimal;

import by.delivery.main_service.repository.entity.ParcelSize;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ParcelRequest(
        @NotNull @Positive @Digits(integer = 4, fraction = 2) BigDecimal weightKg,
        @NotNull ParcelSize size,
        String description) {
}