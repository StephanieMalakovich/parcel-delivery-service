package by.delivery.stats_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import by.delivery.stats_service.repository.entity.OrderFact;

public interface OrderFactRepository extends JpaRepository<OrderFact, Integer> {
}
