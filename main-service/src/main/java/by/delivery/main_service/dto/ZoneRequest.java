package by.delivery.main_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ZoneRequest(@NotBlank @Size(max = 255) String name) {
}
