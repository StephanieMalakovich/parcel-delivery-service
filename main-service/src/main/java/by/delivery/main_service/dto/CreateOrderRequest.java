package by.delivery.main_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotNull Integer zoneId,
        @NotBlank @Size(max = 255) String pickupAddress,
        @NotBlank @Size(max = 255) String deliveryAddress,
        @NotNull @Valid ParcelRequest parcel) {
}
