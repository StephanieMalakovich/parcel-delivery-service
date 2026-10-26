package by.delivery.main_service.dto;

import java.time.LocalDateTime;

import by.delivery.main_service.repository.entity.NotificationType;
import by.delivery.main_service.repository.entity.UserNotification;

public record NotificationResponse(
        Integer id,
        NotificationType type,
        String text,
        boolean read,
        LocalDateTime createdAt) {

    public static NotificationResponse from(UserNotification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getText(),
                Boolean.TRUE.equals(notification.getIsRead()),
                notification.getCreatedAt());
    }
}
