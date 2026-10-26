package by.delivery.main_service.dto;

import java.util.List;

import by.delivery.main_service.repository.entity.Courier;
import by.delivery.main_service.repository.entity.CourierStatus;

public record CourierResponse(
        Integer id,
        Integer userId,
        String fullName,
        CourierStatus status,
        String phone,
        List<Integer> zoneIds) {

    public static CourierResponse from(Courier courier, List<Integer> zoneIds) {
        return new CourierResponse(
                courier.getId(),
                courier.getUserId(),
                courier.getFullName(),
                courier.getStatus(),
                courier.getPhone(),
                zoneIds);
    }
}
