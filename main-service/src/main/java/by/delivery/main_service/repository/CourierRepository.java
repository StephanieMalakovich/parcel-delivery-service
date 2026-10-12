package by.delivery.main_service.repository;

import by.delivery.main_service.repository.entity.Courier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierRepository extends JpaRepository<Courier, Integer> {
}
