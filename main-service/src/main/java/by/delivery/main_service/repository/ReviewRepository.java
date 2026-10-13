package by.delivery.main_service.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import by.delivery.main_service.repository.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Integer> {
    Optional<Review> findByOrder_Id(Integer orderId);

    boolean existsByOrder_Id(Integer orderId);

    Page<Review> findByCourier_Id(
            Integer courierId,
            Pageable pageable);
}
