package by.delivery.main_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import by.delivery.main_service.repository.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Integer> {
}
