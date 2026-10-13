package by.delivery.main_service.repository;

import by.delivery.main_service.repository.entity.CourierZone;
import by.delivery.main_service.repository.entity.CourierZoneId;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierZoneRepository extends JpaRepository<CourierZone, CourierZoneId> {
    List<CourierZone> findByCourier_Id(Integer courierId);

    List<CourierZone> findByZone_Id(Integer zoneId);

    boolean existsByCourier_IdAndZone_Id(
            Integer courierId,
            Integer zoneId);

    void deleteByCourier_IdAndZone_Id(
            Integer courierId,
            Integer zoneId);
}
