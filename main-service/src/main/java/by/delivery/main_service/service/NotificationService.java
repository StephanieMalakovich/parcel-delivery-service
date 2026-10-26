package by.delivery.main_service.service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.delivery.main_service.dto.NotificationResponse;
import by.delivery.main_service.dto.PageResponse;
import by.delivery.main_service.repository.UserNotificationRepository;
import by.delivery.main_service.repository.entity.NotificationType;
import by.delivery.main_service.repository.entity.UserNotification;

import jakarta.persistence.EntityNotFoundException;
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

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getNotifications(Integer userId, boolean unreadOnly, Pageable pageable) {
        Page<UserNotification> notifications = unreadOnly
                ? notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.of(notifications.map(NotificationResponse::from));
    }

    @Transactional(readOnly = true)
    public long countUnread(Integer userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markRead(Integer userId, Integer notificationId) {
        UserNotification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException("Уведомление не найдено: " + notificationId));
        if (!Objects.equals(notification.getUserId(), userId)) {
            throw new SecurityException("Уведомление принадлежит другому пользователю");
        }
        notification.setIsRead(true);
        return NotificationResponse.from(notification);
    }
}
