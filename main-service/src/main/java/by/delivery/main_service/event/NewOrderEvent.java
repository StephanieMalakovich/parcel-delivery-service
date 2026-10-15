package by.delivery.main_service.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import by.delivery.main_service.repository.entity.Order;
import by.delivery.main_service.repository.entity.OrderStatus;
import by.delivery.main_service.repository.entity.Parcel;
import by.delivery.main_service.repository.entity.ParcelSize;

public record NewOrderEvent(
        UUID eventId,
        String type,
        LocalDateTime occurredAt,
        Integer orderId,
        Integer zoneId,
        Integer clientId,
        String pickupAddress,
        String deliveryAddress,
        BigDecimal weightKg,
        ParcelSize size,
        OrderStatus status,
        LocalDateTime createdAt) {

    public static final String TYPE = "NEW_ORDER";

    public static NewOrderEvent from(Order order) {
        Parcel parcel = order.getParcel();
        return new NewOrderEvent(
                UUID.randomUUID(),
                TYPE,
                LocalDateTime.now(),
                order.getId(),
                order.getZone().getId(),
                order.getClient().getId(),
                order.getPickupAddress(),
                order.getDeliveryAddress(),
                parcel == null ? null : parcel.getWeightKg(),
                parcel == null ? null : parcel.getSize(),
                order.getStatus(),
                order.getCreatedAt());
    }
}
