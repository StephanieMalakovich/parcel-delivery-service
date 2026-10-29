package by.delivery.main_service.controller;

import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import by.delivery.main_service.dto.NotificationResponse;
import by.delivery.main_service.dto.PageResponse;
import by.delivery.main_service.security.CurrentUser;
import by.delivery.main_service.service.NotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final CurrentUser currentUser;
    private final NotificationService notificationService;

    @PreAuthorize("hasAnyRole('CUSTOMER', 'COURIER', 'ADMIN')")
    @GetMapping
    public PageResponse<NotificationResponse> getAll(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return notificationService.getNotifications(currentUser.id(), unreadOnly, pageable);
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'COURIER', 'ADMIN')")
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.countUnread(currentUser.id()));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'COURIER', 'ADMIN')")
    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(
            @PathVariable Integer id) {
        return notificationService.markRead(currentUser.id(), id);
    }
}
