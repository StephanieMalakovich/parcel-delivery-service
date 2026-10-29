package by.delivery.main_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateClientRequest(
        @NotBlank @Size(max = 36) String userId,
        @NotBlank @Size(max = 255) String fullName,
        @Pattern(regexp = "\\d{1,11}") String phone) {
}
