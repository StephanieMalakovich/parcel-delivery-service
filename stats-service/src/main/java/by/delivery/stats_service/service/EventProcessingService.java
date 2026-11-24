package by.delivery.stats_service.service;

import java.sql.Types;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.delivery.stats.grpc.CourierUpdated;
import by.delivery.stats.grpc.OrderCancelled;
import by.delivery.stats.grpc.OrderDelivered;
import by.delivery.stats.grpc.RecordAnalyticsEventRequest;
import by.delivery.stats.grpc.ReviewCreated;
import by.delivery.stats_service.grpc.StatisticsProtoMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventProcessingService {

    private final JdbcClient jdbc;
    private final StatisticsProtoMapper mapper;

    @Transactional
    public void record(RecordAnalyticsEventRequest event) {
        UUID eventId = UUID.fromString(event.getEventId());
        require(event.getFactCase() != RecordAnalyticsEventRequest.FactCase.FACT_NOT_SET, "Не указан тип факта");

        int inserted = jdbc.sql("""
                INSERT INTO processed_events (event_id) VALUES (:id)
                ON CONFLICT (event_id) DO NOTHING
                """).param("id", eventId).update();
        if (inserted == 0) {
            return;
        }

        // The marker and the fact commit together; an invalid payload leaves neither
        // behind.
        switch (event.getFactCase()) {
            case ORDER_DELIVERED -> apply(event.getOrderDelivered());
            case ORDER_CANCELLED -> apply(event.getOrderCancelled());
            case REVIEW_CREATED -> apply(event.getReviewCreated());
            case COURIER_UPDATED -> apply(event.getCourierUpdated());
            case FACT_NOT_SET -> throw new IllegalArgumentException("Не указан тип факта");
        }
    }

    private void apply(OrderDelivered fact) {
        require(fact.getOrderId() > 0 && fact.getCourierId() > 0, "Некорректный ID заказа или курьера");
        require(fact.hasCreatedAt() && fact.hasDeliveredAt(), "Не указано время создания или доставки");
        writeOrder(fact.getOrderId(), fact.getCourierId(), "DELIVERED", null,
                mapper.toLocalDateTime(fact.getCreatedAt()),
                fact.hasAssignedAt() ? mapper.toLocalDateTime(fact.getAssignedAt()) : null,
                fact.hasPickedUpAt() ? mapper.toLocalDateTime(fact.getPickedUpAt()) : null,
                mapper.toLocalDateTime(fact.getDeliveredAt()), null);
    }

    private void apply(OrderCancelled fact) {
        require(fact.getOrderId() > 0 && (!fact.hasCourierId() || fact.getCourierId() > 0),
                "Некорректный ID заказа или курьера");
        require(fact.hasCreatedAt() && fact.hasCancelledAt(), "Не указано время создания или отмены");
        String initiator = switch (fact.getCancelledBy()) {
            case CLIENT -> "CLIENT";
            case ADMIN -> "ADMIN";
            default -> throw new IllegalArgumentException("Не указан инициатор отмены");
        };
        writeOrder(fact.getOrderId(), fact.hasCourierId() ? fact.getCourierId() : null, "CANCELLED", initiator,
                mapper.toLocalDateTime(fact.getCreatedAt()),
                fact.hasAssignedAt() ? mapper.toLocalDateTime(fact.getAssignedAt()) : null,
                fact.hasPickedUpAt() ? mapper.toLocalDateTime(fact.getPickedUpAt()) : null,
                null, mapper.toLocalDateTime(fact.getCancelledAt()));
    }

    private void writeOrder(int id, Integer courierId, String status, String cancelledBy, LocalDateTime createdAt,
            LocalDateTime assignedAt, LocalDateTime pickedUpAt, LocalDateTime deliveredAt,
            LocalDateTime cancelledAt) {
        jdbc.sql("""
                INSERT INTO order_facts
                    (order_id, courier_id, status, cancelled_by, created_at, assigned_at,
                     picked_up_at, delivered_at, cancelled_at)
                VALUES (:id, :courier, :status, :cancelledBy, :created, :assigned, :pickedUp, :delivered, :cancelled)
                ON CONFLICT (order_id) DO UPDATE SET
                    courier_id = EXCLUDED.courier_id, status = EXCLUDED.status, cancelled_by = EXCLUDED.cancelled_by,
                    created_at = EXCLUDED.created_at, assigned_at = EXCLUDED.assigned_at,
                    picked_up_at = EXCLUDED.picked_up_at, delivered_at = EXCLUDED.delivered_at,
                    cancelled_at = EXCLUDED.cancelled_at
                WHERE COALESCE(order_facts.cancelled_at, order_facts.delivered_at)
                    <= COALESCE(EXCLUDED.cancelled_at, EXCLUDED.delivered_at)
                """).param("id", id)
                .param("courier", courierId, Types.INTEGER)
                .param("status", status)
                .param("cancelledBy", cancelledBy, Types.VARCHAR)
                .param("created", createdAt, Types.TIMESTAMP)
                .param("assigned", assignedAt, Types.TIMESTAMP)
                .param("pickedUp", pickedUpAt, Types.TIMESTAMP)
                .param("delivered", deliveredAt, Types.TIMESTAMP)
                .param("cancelled", cancelledAt, Types.TIMESTAMP)
                .update();
    }

    private void apply(ReviewCreated fact) {
        require(fact.getReviewId() > 0 && fact.getOrderId() > 0 && fact.getCourierId() > 0,
                "Некорректный ID отзыва, заказа или курьера");
        require(fact.getRating() >= 1 && fact.getRating() <= 5 && fact.hasCreatedAt(),
                "Некорректная оценка или не указано время отзыва");
        jdbc.sql("""
                INSERT INTO review_facts (review_id, order_id, courier_id, rating, created_at)
                VALUES (:id, :orderId, :courier, :rating, :created)
                ON CONFLICT (review_id) DO NOTHING
                """).param("id", fact.getReviewId())
                .param("orderId", fact.getOrderId())
                .param("courier", fact.getCourierId())
                .param("rating", fact.getRating())
                .param("created", mapper.toLocalDateTime(fact.getCreatedAt()), Types.TIMESTAMP)
                .update();
    }

    private void apply(CourierUpdated fact) {
        String name = fact.getFullName().trim();
        require(fact.getCourierId() > 0 && !name.isBlank() && name.length() <= 255 && fact.hasUpdatedAt(),
                "Некорректный ID, имя или время обновления курьера");
        jdbc.sql("""
                INSERT INTO courier_facts (courier_id, full_name, updated_at)
                VALUES (:id, :name, :updated)
                ON CONFLICT (courier_id) DO UPDATE SET
                    full_name = EXCLUDED.full_name, updated_at = EXCLUDED.updated_at
                WHERE courier_facts.updated_at IS NULL OR courier_facts.updated_at <= EXCLUDED.updated_at
                """).param("id", fact.getCourierId())
                .param("name", name)
                .param("updated", mapper.toInstant(fact.getUpdatedAt()).atOffset(ZoneOffset.UTC))
                .update();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
