package by.delivery.main_service.dto;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCourierRequest(
        @NotNull Integer userId,
        @NotBlank @Size(max = 255) String fullName,
        @Pattern(regexp = "\\d{1,11}") String phone,
        Set<Integer> zoneIds) {
}
