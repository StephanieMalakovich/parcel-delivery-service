package by.delivery.stats_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import by.delivery.stats_service.repository.entity.ReviewFact;

public interface ReviewFactRepository extends JpaRepository<ReviewFact, Integer> {
}
