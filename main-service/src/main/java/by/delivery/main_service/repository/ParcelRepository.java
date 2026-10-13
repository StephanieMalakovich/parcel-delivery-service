package by.delivery.main_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import by.delivery.main_service.repository.entity.Parcel;

public interface ParcelRepository extends JpaRepository<Parcel, Integer> {
}
