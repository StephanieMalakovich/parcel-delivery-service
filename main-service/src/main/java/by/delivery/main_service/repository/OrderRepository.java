package by.delivery.main_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import by.delivery.main_service.repository.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Integer> {
}
