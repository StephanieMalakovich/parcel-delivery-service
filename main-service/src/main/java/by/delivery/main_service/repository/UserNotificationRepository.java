package by.delivery.main_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import by.delivery.main_service.repository.entity.UserNotification;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Integer> {
    Page<UserNotification> findByUserIdOrderByCreatedAtDesc(
            Integer userId,
            Pageable pageable);

    Page<UserNotification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(
            Integer userId,
            Pageable pageable);

    long countByUserIdAndIsReadFalse(Integer userId);
}
