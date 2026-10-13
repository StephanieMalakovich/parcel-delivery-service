package by.delivery.main_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import by.delivery.main_service.repository.entity.UserNotification;

public interface NotificationRepository extends JpaRepository<UserNotification, Integer> {
}
