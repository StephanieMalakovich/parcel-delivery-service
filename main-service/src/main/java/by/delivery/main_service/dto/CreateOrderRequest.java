package by.delivery.main_service.dto;

public record CreateOrderRequest(Integer zoneId, String pickupAddress, String deliveryAddress, ParcelRequest parcel) {
}
