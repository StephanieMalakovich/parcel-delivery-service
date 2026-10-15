package by.delivery.main_service.dto;

import java.math.BigDecimal;

import by.delivery.main_service.repository.entity.Parcel;
import by.delivery.main_service.repository.entity.ParcelSize;

public record ParcelResponse(BigDecimal weightKg, ParcelSize size, String description) {

    public static ParcelResponse from(Parcel parcel) {
        return parcel == null ? null : new ParcelResponse(parcel.getWeightKg(), parcel.getSize(), parcel.getDescription());
    }
}
