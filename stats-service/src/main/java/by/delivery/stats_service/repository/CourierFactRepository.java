package by.delivery.stats_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import by.delivery.stats_service.repository.entity.CourierFact;

public interface CourierFactRepository extends JpaRepository<CourierFact, Integer> {
}
