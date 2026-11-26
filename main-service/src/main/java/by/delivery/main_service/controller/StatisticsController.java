package by.delivery.main_service.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import by.delivery.main_service.dto.StatisticsResponse;
import by.delivery.main_service.repository.CourierRepository;
import by.delivery.main_service.security.CurrentUser;
import by.delivery.main_service.statistics.StatisticsGrpcClient;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
@Slf4j
public class StatisticsController {

    private final CurrentUser currentUser;
    private final CourierRepository courierRepository;
    private final StatisticsGrpcClient statistics;

    @PreAuthorize("hasAnyRole('COURIER', 'ADMIN')")
    @GetMapping
    public StatisticsResponse getStatistics(@RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            HttpServletResponse response) {
        String requestId = UUID.randomUUID().toString();
        response.setHeader("X-Request-ID", requestId);
        boolean admin = currentUser.isAdmin();
        log.info("[REST] requestId={} GET /stats role={}", requestId, admin ? "ADMIN" : "COURIER");
        var period = StatisticsGrpcClient.period(from, to);
        if (admin) {
            return StatisticsResponse.Admin.from(statistics.getAdminOverview(requestId, period));
        }
        int courierId = courierRepository.findByUserId(currentUser.id())
                .orElseThrow(() -> new EntityNotFoundException("Профиль курьера не найден"))
                .getId();
        return StatisticsResponse.Courier.from(statistics.getCourierStats(requestId, courierId, period));
    }
}
