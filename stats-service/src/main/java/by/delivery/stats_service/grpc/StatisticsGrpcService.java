package by.delivery.stats_service.grpc;

import java.util.function.Supplier;

import org.springframework.grpc.server.service.GrpcService;

import by.delivery.stats.grpc.AdminOverviewRequest;
import by.delivery.stats.grpc.AdminOverviewResponse;
import by.delivery.stats.grpc.CourierStatsRequest;
import by.delivery.stats.grpc.CourierStatsResponse;
import by.delivery.stats.grpc.RecordAnalyticsEventRequest;
import by.delivery.stats.grpc.RecordAnalyticsEventResponse;
import by.delivery.stats.grpc.StatisticsServiceGrpc;
import by.delivery.stats_service.service.EventProcessingService;
import by.delivery.stats_service.service.StatisticsService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class StatisticsGrpcService extends StatisticsServiceGrpc.StatisticsServiceImplBase {

    private final StatisticsService statisticsService;
    private final EventProcessingService eventProcessingService;
    private final StatisticsProtoMapper mapper;

    @Override
    public void getCourierStats(CourierStatsRequest request, StreamObserver<CourierStatsResponse> responseObserver) {
        respond(responseObserver, "GetCourierStats", () -> {
            var period = mapper.toPeriod(request.hasPeriod(), request.getPeriod());
            var stats = statisticsService.getCourierStats(request.getCourierId(), period);
            log.info("[gRPC] requestId={} courierId={} delivered={}",
                    RequestIdInterceptor.REQUEST_ID.get(), stats.courierId(), stats.deliveredCount());
            return mapper.toResponse(stats);
        });
    }

    @Override
    public void getAdminOverview(AdminOverviewRequest request, StreamObserver<AdminOverviewResponse> responseObserver) {
        respond(responseObserver, "GetAdminOverview", () -> {
            var period = mapper.toPeriod(request.hasPeriod(), request.getPeriod());
            var overview = statisticsService.getAdminOverview(period);
            log.info("[gRPC] requestId={} delivered={} cancelled={}",
                    RequestIdInterceptor.REQUEST_ID.get(), overview.deliveredCount(), overview.cancelledCount());
            return mapper.toResponse(overview);
        });
    }

    @Override
    public void recordAnalyticsEvent(RecordAnalyticsEventRequest request,
            StreamObserver<RecordAnalyticsEventResponse> responseObserver) {
        respond(responseObserver, "RecordAnalyticsEvent", () -> {
            eventProcessingService.record(request);
            log.info("[gRPC] eventId={} fact={} saved or already applied", request.getEventId(), request.getFactCase());
            return RecordAnalyticsEventResponse.getDefaultInstance();
        });
    }

    private <T> void respond(StreamObserver<T> responseObserver, String method, Supplier<T> call) {
        String requestId = RequestIdInterceptor.REQUEST_ID.get();
        log.info("[gRPC] requestId={} {} started", requestId, method);
        T response;
        try {
            response = call.get();
        } catch (IllegalArgumentException e) {
            log.warn("[gRPC] requestId={} {}: некорректный запрос", requestId, method);
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
            return;
        } catch (RuntimeException e) {
            log.error("[gRPC] requestId={} {} failed", requestId, method, e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Не удалось обработать запрос статистики")
                    .asRuntimeException());
            return;
        }
        responseObserver.onNext(response);
        responseObserver.onCompleted();
        log.info("[gRPC] requestId={} {} completed", requestId, method);
    }
}
