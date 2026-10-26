package by.delivery.main_service.dto;

import java.time.LocalDateTime;
import java.util.List;

import by.delivery.main_service.repository.entity.CancelledBy;
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
        CancelledBy cancelledBy,
        LocalDateTime createdAt,
        List<OrderStatusHistoryResponse> statusHistory) {

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
                o.getCancelledBy(),
                o.getCreatedAt(),
                o.getStatusHistory().stream().map(OrderStatusHistoryResponse::from).toList());
    }

    public LocalDateTime statusTime(OrderStatus searchedStatus) {
        return statusHistory.stream()
                .filter(entry -> entry.status() == searchedStatus)
                .map(OrderStatusHistoryResponse::changedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }
}