package by.delivery.main_service.statistics;

import java.time.Instant;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.google.protobuf.Timestamp;

import by.delivery.stats.grpc.AdminOverviewRequest;
import by.delivery.stats.grpc.AdminOverviewResponse;
import by.delivery.stats.grpc.CourierStatsRequest;
import by.delivery.stats.grpc.CourierStatsResponse;
import by.delivery.stats.grpc.Period;
import by.delivery.stats.grpc.RecordAnalyticsEventRequest;
import by.delivery.stats.grpc.StatisticsServiceGrpc.StatisticsServiceBlockingStub;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class StatisticsGrpcClient {

    private static final Metadata.Key<String> REQUEST_ID = Metadata.Key.of("x-request-id",
            Metadata.ASCII_STRING_MARSHALLER);

    private final StatisticsServiceBlockingStub stub;

    public CourierStatsResponse getCourierStats(String requestId, int courierId, Period period) {
        var request = CourierStatsRequest.newBuilder().setCourierId(courierId).setPeriod(period).build();
        return query(requestId, "GetCourierStats", () -> withRequestId(requestId).getCourierStats(request));
    }

    public AdminOverviewResponse getAdminOverview(String requestId, Period period) {
        var request = AdminOverviewRequest.newBuilder().setPeriod(period).build();
        return query(requestId, "GetAdminOverview", () -> withRequestId(requestId).getAdminOverview(request));
    }

    void record(RecordAnalyticsEventRequest event) {
        withRequestId(event.getEventId()).recordAnalyticsEvent(event);
    }

    public static Period period(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("Начало периода должно быть раньше конца");
        }
        var period = Period.newBuilder();
        if (from != null) {
            period.setFrom(timestamp(from));
        }
        if (to != null) {
            period.setTo(timestamp(to));
        }
        return period.build();
    }

    static Timestamp timestamp(Instant value) {
        return Timestamp.newBuilder().setSeconds(value.getEpochSecond()).setNanos(value.getNano()).build();
    }

    private StatisticsServiceBlockingStub withRequestId(String requestId) {
        Metadata metadata = new Metadata();
        metadata.put(REQUEST_ID, requestId);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    private <T> T query(String requestId, String method, Supplier<T> call) {
        log.info("[gRPC] requestId={} sending {}", requestId, method);
        try {
            T response = call.get();
            log.info("[gRPC] requestId={} received {}", requestId, method);
            return response;
        } catch (StatusRuntimeException e) {
            var code = e.getStatus().getCode();
            log.warn("[gRPC] requestId={} {} failed: {}", requestId, method, code);
            throw switch (code) {
                case INVALID_ARGUMENT -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Некорректный запрос статистики");
                case UNAVAILABLE -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Сервис статистики недоступен");
                case DEADLINE_EXCEEDED -> new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                        "Сервис статистики не ответил вовремя");
                default -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Ошибка сервиса статистики");
            };
        }
    }
}
