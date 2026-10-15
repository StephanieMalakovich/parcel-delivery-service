package by.delivery.main_service.service;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.delivery.main_service.repository.UserNotificationRepository;
import by.delivery.main_service.repository.entity.NotificationType;
import by.delivery.main_service.repository.entity.UserNotification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserNotificationRepository notificationRepository;

    @Transactional
    public void notifyUser(Integer userId, NotificationType type, String text) {
        notifyUsers(List.of(userId), type, text);
    }

    @Transactional
    public void notifyUsers(Collection<Integer> userIds, NotificationType type, String text) {
        List<UserNotification> notifications = userIds.stream()
                .distinct()
                .map(userId -> createNotification(userId, type, text))
                .toList();

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }
    }

    private UserNotification createNotification(Integer userId, NotificationType type, String text) {
        UserNotification notification = new UserNotification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setText(text);
        return notification;
    }
}