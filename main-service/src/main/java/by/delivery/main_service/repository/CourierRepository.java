package by.delivery.main_service.repository;

import by.delivery.main_service.repository.entity.Courier;
import by.delivery.main_service.repository.entity.CourierStatus;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierRepository extends JpaRepository<Courier, Integer> {
    Optional<Courier> findByUserId(String userId);

    boolean existsByUserId(String userId);

    List<Courier> findByStatus(CourierStatus status);
}
