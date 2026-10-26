package by.delivery.main_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateClientRequest(
        @NotBlank @Size(max = 255) String fullName,
        @Pattern(regexp = "\\d{1,11}") String phone) {
}
