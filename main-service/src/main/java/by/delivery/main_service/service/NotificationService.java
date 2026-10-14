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
    public void notifyUsers(Collection<Integer> userIds, NotificationType type, String text) {
        List<UserNotification> notifications = userIds.stream()
                .distinct()
                .map(userId -> {
                    UserNotification n = new UserNotification();
                    n.setUserId(userId);
                    n.setType(type);
                    n.setText(text);
                    return n;
                })
                .toList();
        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }
    }
}
