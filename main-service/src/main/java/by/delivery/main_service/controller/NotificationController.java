package by.delivery.main_service.controller;

import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import by.delivery.main_service.dto.NotificationResponse;
import by.delivery.main_service.dto.PageResponse;
import by.delivery.main_service.service.NotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final NotificationService notificationService;

    @GetMapping
    public PageResponse<NotificationResponse> getAll(
            @RequestHeader(USER_ID_HEADER) Integer userId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return notificationService.getNotifications(userId, unreadOnly, pageable);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@RequestHeader(USER_ID_HEADER) Integer userId) {
        return Map.of("count", notificationService.countUnread(userId));
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(
            @RequestHeader(USER_ID_HEADER) Integer userId,
            @PathVariable Integer id) {
        return notificationService.markRead(userId, id);
    }
}
