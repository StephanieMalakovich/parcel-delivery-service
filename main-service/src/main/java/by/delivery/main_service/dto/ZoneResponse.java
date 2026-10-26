package by.delivery.main_service.dto;

import by.delivery.main_service.repository.entity.Zone;

public record ZoneResponse(Integer id, String name) {

    public static ZoneResponse from(Zone zone) {
        return new ZoneResponse(zone.getId(), zone.getName());
    }
}
