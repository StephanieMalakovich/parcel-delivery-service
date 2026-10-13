package by.delivery.main_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import by.delivery.main_service.repository.entity.Parcel;

public interface ParcelRepository extends JpaRepository<Parcel, Integer> {

    Optional<Parcel> findByOrder_Id(Integer orderId);

    boolean existsByOrder_Id(Integer orderId);
}
