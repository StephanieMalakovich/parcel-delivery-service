package by.delivery.main_service.statistics;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.google.protobuf.Timestamp;

import by.delivery.main_service.repository.entity.Courier;
import by.delivery.main_service.repository.entity.Order;
import by.delivery.main_service.repository.entity.OrderStatus;
import by.delivery.main_service.repository.entity.Review;
import by.delivery.stats.grpc.CourierUpdated;
import by.delivery.stats.grpc.OrderCancelled;
import by.delivery.stats.grpc.OrderDelivered;
import by.delivery.stats.grpc.RecordAnalyticsEventRequest;
import by.delivery.stats.grpc.ReviewCreated;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class AnalyticsOutbox {

    private final JdbcClient jdbc;
    private final ZoneId zone;

    AnalyticsOutbox(JdbcClient jdbc, @Value("${app.statistics.time-zone:}") String timeZone) {
        this.jdbc = jdbc;
        this.zone = timeZone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(timeZone);
    }

    public void orderDelivered(Order order) {
        var fact = OrderDelivered.newBuilder()
                .setOrderId(order.getId())
                .setCourierId(order.getCourier().getId())
                .setCreatedAt(timestamp(order.getCreatedAt()))
                .setDeliveredAt(timestamp(order.findStatusTime(OrderStatus.DELIVERED)));
        var assignedAt = order.findStatusTime(OrderStatus.COURIER_ASSIGNED);
        var pickedUpAt = order.findStatusTime(OrderStatus.PICKED_UP);
        if (assignedAt != null) {
            fact.setAssignedAt(timestamp(assignedAt));
        }
        if (pickedUpAt != null) {
            fact.setPickedUpAt(timestamp(pickedUpAt));
        }
        enqueue(event().setOrderDelivered(fact).build());
    }

    public void orderCancelled(Order order) {
        var initiator = switch (order.getCancelledBy()) {
            case CLIENT -> OrderCancelled.Initiator.CLIENT;
            case ADMIN -> OrderCancelled.Initiator.ADMIN;
        };
        var fact = OrderCancelled.newBuilder()
                .setOrderId(order.getId())
                .setCancelledBy(initiator)
                .setCreatedAt(timestamp(order.getCreatedAt()))
                .setCancelledAt(timestamp(order.findStatusTime(OrderStatus.CANCELLED)));
        if (order.getCourier() != null) {
            fact.setCourierId(order.getCourier().getId());
        }
        var assignedAt = order.findStatusTime(OrderStatus.COURIER_ASSIGNED);
        var pickedUpAt = order.findStatusTime(OrderStatus.PICKED_UP);
        if (assignedAt != null) {
            fact.setAssignedAt(timestamp(assignedAt));
        }
        if (pickedUpAt != null) {
            fact.setPickedUpAt(timestamp(pickedUpAt));
        }
        enqueue(event().setOrderCancelled(fact).build());
    }

    public void reviewCreated(Review review) {
        var fact = ReviewCreated.newBuilder()
                .setReviewId(review.getId())
                .setOrderId(review.getOrder().getId())
                .setCourierId(review.getCourier().getId())
                .setRating(review.getRating())
                .setCreatedAt(timestamp(review.getCreatedAt()));
        enqueue(event().setReviewCreated(fact).build());
    }

    public void courierUpdated(Courier courier) {
        var fact = CourierUpdated.newBuilder()
                .setCourierId(courier.getId())
                .setFullName(courier.getFullName())
                .setUpdatedAt(StatisticsGrpcClient.timestamp(Instant.now()));
        enqueue(event().setCourierUpdated(fact).build());
    }

    private static RecordAnalyticsEventRequest.Builder event() {
        return RecordAnalyticsEventRequest.newBuilder().setEventId(UUID.randomUUID().toString());
    }

    private void enqueue(RecordAnalyticsEventRequest event) {
        jdbc.sql("INSERT INTO statistics_outbox (payload) VALUES (:payload)")
                .param("payload", event.toByteArray()).update();
    }

    private Timestamp timestamp(LocalDateTime value) {
        return StatisticsGrpcClient.timestamp(value.atZone(zone).toInstant());
    }
}
