package by.delivery.main_service.dto;

import java.time.LocalDateTime;

import by.delivery.main_service.repository.entity.CancellBy;
import by.delivery.main_service.repository.entity.Order;
import by.delivery.main_service.repository.entity.OrderStatus;

public record OrderResponse(
        Integer id,
        OrderStatus status,
        Integer clientId,
        Integer courierId,
        Integer zoneId,
        String pickupAddress,
        String deliveryAddress,
        ParcelResponse parcel,
        CancellBy cancelledBy,
        LocalDateTime createdAt,
        LocalDateTime assignedAt,
        LocalDateTime pickedUpAt,
        LocalDateTime deliveredAt,
        LocalDateTime cancelledAt) {

    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.getId(),
                o.getStatus(),
                o.getClient().getId(),
                o.getCourier() == null ? null : o.getCourier().getId(),
                o.getZone().getId(),
                o.getPickupAddress(),
                o.getDeliveryAddress(),
                ParcelResponse.from(o.getParcel()),
                o.getRole(),
                o.getCreatedAt(),
                o.getAssignedAt(),
                o.getPickedUpAt(),
                o.getDeliveredAt(),
                o.getCancelledAt());
    }
}
