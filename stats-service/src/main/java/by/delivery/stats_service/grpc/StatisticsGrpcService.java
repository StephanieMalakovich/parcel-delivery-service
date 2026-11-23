package by.delivery.stats_service.grpc;

import java.util.function.Supplier;

import org.springframework.grpc.server.service.GrpcService;

import by.delivery.stats.grpc.AdminOverviewRequest;
import by.delivery.stats.grpc.AdminOverviewResponse;
import by.delivery.stats.grpc.CourierStatsRequest;
import by.delivery.stats.grpc.CourierStatsResponse;
import by.delivery.stats.grpc.StatisticsServiceGrpc;
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
    private final StatisticsProtoMapper mapper;

    @Override
    public void getCourierStats(CourierStatsRequest request, StreamObserver<CourierStatsResponse> responseObserver) {
        respond(responseObserver, "GetCourierStats", () -> {
            var period = mapper.toPeriod(request.hasPeriod(), request.getPeriod());
            return mapper.toResponse(statisticsService.getCourierStats(request.getCourierId(), period));
        });
    }

    @Override
    public void getAdminOverview(AdminOverviewRequest request, StreamObserver<AdminOverviewResponse> responseObserver) {
        respond(responseObserver, "GetAdminOverview", () -> {
            var period = mapper.toPeriod(request.hasPeriod(), request.getPeriod());
            return mapper.toResponse(statisticsService.getAdminOverview(period));
        });
    }


    private <T> void respond(StreamObserver<T> responseObserver, String method, Supplier<T> call) {
        T response;
        try {
            response = call.get();
        } catch (IllegalArgumentException e) {
            log.warn("[gRPC] {}: некорректный запрос: {}", method, e.getMessage());
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
            return;
        } catch (RuntimeException e) {
            log.error("[gRPC] {}: ошибка при расчёте статистики", method, e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Не удалось рассчитать статистику")
                    .asRuntimeException());
            return;
        }
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
