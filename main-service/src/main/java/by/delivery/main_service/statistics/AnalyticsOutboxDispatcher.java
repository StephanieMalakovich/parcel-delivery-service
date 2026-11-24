package by.delivery.main_service.statistics;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.google.protobuf.InvalidProtocolBufferException;

import by.delivery.stats.grpc.RecordAnalyticsEventRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsOutboxDispatcher {

    private static final int BATCH_SIZE = 20;

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final StatisticsGrpcClient statistics;

    @Scheduled(initialDelayString = "${app.statistics.outbox.poll-delay:1s}", fixedDelayString = "${app.statistics.outbox.poll-delay:1s}")
    public void dispatchAvailable() {
        for (int count = 0; count < BATCH_SIZE; count++) {
            if (!Boolean.TRUE.equals(transaction.execute(status -> dispatchNext()))) {
                break;
            }
        }
    }

    private boolean dispatchNext() {
        var candidate = jdbc.sql("""
                SELECT id, payload, attempts FROM statistics_outbox
                WHERE failed_at IS NULL AND next_attempt_at <= now()
                ORDER BY next_attempt_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
                """).query((rs, row) -> new Pending(rs.getLong("id"), rs.getBytes("payload"), rs.getInt("attempts")))
                .optional();
        if (candidate.isEmpty()) {
            return false;
        }
        var pending = candidate.get();
        RecordAnalyticsEventRequest event;
        try {
            event = RecordAnalyticsEventRequest.parseFrom(pending.payload());
        } catch (InvalidProtocolBufferException e) {
            markFailed(pending.id(), "invalid protobuf");
            return true;
        }

        try {
            statistics.record(event);
        } catch (StatusRuntimeException e) {
            var code = e.getStatus().getCode();
            if (code == Status.Code.INVALID_ARGUMENT) {
                markFailed(pending.id(), code.name());
                return true;
            }
            // Keep the same event ID even if the remote transaction committed before the
            // response was lost.
            int retryDelaySeconds = Math.min(60, 1 << Math.min(pending.attempts(), 6));
            jdbc.sql("""
                    UPDATE statistics_outbox SET attempts = attempts + 1,
                        next_attempt_at = now() + (:seconds * interval '1 second')
                    WHERE id = :id
                    """).param("seconds", retryDelaySeconds).param("id", pending.id()).update();
            log.warn("[gRPC] eventId={} RecordAnalyticsEvent {}: retry in {}s",
                    event.getEventId(), code, retryDelaySeconds);
            return false;
        }

        jdbc.sql("DELETE FROM statistics_outbox WHERE id = :id").param("id", pending.id()).update();
        log.info("[gRPC] eventId={} fact={} acknowledged", event.getEventId(), event.getFactCase());
        return true;
    }

    private void markFailed(long id, String reason) {
        jdbc.sql("UPDATE statistics_outbox SET failed_at = now() WHERE id = :id").param("id", id).update();
        log.error("[gRPC] outboxId={} RecordAnalyticsEvent rejected: {}", id, reason);
    }

    private record Pending(long id, byte[] payload, int attempts) {
    }
}
