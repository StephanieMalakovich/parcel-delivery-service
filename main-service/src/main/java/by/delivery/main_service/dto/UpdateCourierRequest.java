package by.delivery.main_service.dto;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCourierRequest(
        @NotBlank @Size(max = 255) String fullName,
        @Pattern(regexp = "\\d{1,11}") String phone,
        Set<Integer> zoneIds) {
}
