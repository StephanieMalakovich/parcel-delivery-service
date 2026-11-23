package by.delivery.stats_service.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.delivery.stats_service.event.AnalyticsEventEnvelope;
import by.delivery.stats_service.event.AnalyticsEventTypes;
import by.delivery.stats_service.event.CourierUpdatedEvent;
import by.delivery.stats_service.event.InvalidEventException;
import by.delivery.stats_service.event.NewReviewEvent;
import by.delivery.stats_service.event.OrderCancelledEvent;
import by.delivery.stats_service.event.OrderDeliveredEvent;
import by.delivery.stats_service.repository.CourierFactRepository;
import by.delivery.stats_service.repository.OrderFactRepository;
import by.delivery.stats_service.repository.ProcessedEventRepository;
import by.delivery.stats_service.repository.ReviewFactRepository;
import by.delivery.stats_service.repository.entity.CourierFact;
import by.delivery.stats_service.repository.entity.FactOrderStatus;
import by.delivery.stats_service.repository.entity.OrderFact;
import by.delivery.stats_service.repository.entity.ProcessedEvent;
import by.delivery.stats_service.repository.entity.ReviewFact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;


@Service
@RequiredArgsConstructor
@Slf4j
public class EventProcessingService {

    private final JsonMapper jsonMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final OrderFactRepository orderFactRepository;
    private final ReviewFactRepository reviewFactRepository;
    private final CourierFactRepository courierFactRepository;
    private final Clock clock;

    @Transactional
    public void handle(String payload) {
        AnalyticsEventEnvelope envelope = parse(payload, AnalyticsEventEnvelope.class);
        require(envelope.eventId() != null, "нет eventId");
        require(envelope.type() != null, "нет type");

        if (processedEventRepository.existsById(envelope.eventId())) {
            log.info("[Broker 2] Событие {} ({}) уже обработано, пропускаем", envelope.eventId(), envelope.type());
            return;
        }

        switch (envelope.type()) {
            case AnalyticsEventTypes.ORDER_DELIVERED -> apply(parse(payload, OrderDeliveredEvent.class));
            case AnalyticsEventTypes.ORDER_CANCELLED -> apply(parse(payload, OrderCancelledEvent.class));
            case AnalyticsEventTypes.NEW_REVIEW -> apply(parse(payload, NewReviewEvent.class));
            case AnalyticsEventTypes.COURIER_UPDATED -> apply(parse(payload, CourierUpdatedEvent.class));
            default -> {
                log.warn("[Broker 2] Неизвестный тип события {}: {}, пропускаем", envelope.type(), envelope.eventId());
                return;
            }
        }

        processedEventRepository.save(new ProcessedEvent(envelope.eventId(), LocalDateTime.now(clock)));
    }

    private void apply(OrderDeliveredEvent event) {
        require(event.orderId() != null, "ORDER_DELIVERED без orderId");
        require(event.courierId() != null, "ORDER_DELIVERED без courierId");
        require(event.createdAt() != null, "ORDER_DELIVERED без createdAt");
        require(event.deliveredAt() != null, "ORDER_DELIVERED без deliveredAt");

        OrderFact fact = orderFactRepository.findById(event.orderId()).orElseGet(OrderFact::new);
        if (isNewerThan(fact, event.deliveredAt())) {
            log.info("[Statistics] Заказ {} уже завершён позже ({}), событие доставки не применяется",
                    event.orderId(), fact.getStatus());
            return;
        }

        fact.setOrderId(event.orderId());
        fact.setCourierId(event.courierId());
        fact.setStatus(FactOrderStatus.DELIVERED);
        fact.setCancelledBy(null);
        fact.setCreatedAt(event.createdAt());
        fact.setAssignedAt(event.assignedAt());
        fact.setPickedUpAt(event.pickedUpAt());
        fact.setDeliveredAt(event.deliveredAt());
        fact.setCancelledAt(null);
        orderFactRepository.save(fact);
        log.info("[Statistics] Сохранён доставленный заказ {}, курьер {}", event.orderId(), event.courierId());
    }

    private void apply(OrderCancelledEvent event) {
        require(event.orderId() != null, "ORDER_CANCELLED без orderId");
        require(event.cancelledBy() != null, "ORDER_CANCELLED без cancelledBy");
        require(event.createdAt() != null, "ORDER_CANCELLED без createdAt");
        require(event.cancelledAt() != null, "ORDER_CANCELLED без cancelledAt");

        OrderFact fact = orderFactRepository.findById(event.orderId()).orElseGet(OrderFact::new);
        if (isNewerThan(fact, event.cancelledAt())) {
            log.info("[Statistics] Заказ {} уже завершён позже ({}), событие отмены не применяется",
                    event.orderId(), fact.getStatus());
            return;
        }

        fact.setOrderId(event.orderId());
        fact.setCourierId(event.courierId());
        fact.setStatus(FactOrderStatus.CANCELLED);
        fact.setCancelledBy(event.cancelledBy());
        fact.setCreatedAt(event.createdAt());
        fact.setAssignedAt(event.assignedAt());
        fact.setPickedUpAt(event.pickedUpAt());
        fact.setDeliveredAt(null);
        fact.setCancelledAt(event.cancelledAt());
        orderFactRepository.save(fact);
        log.info("[Statistics] Сохранён отменённый заказ {}, отменил {}", event.orderId(), event.cancelledBy());
    }

    private void apply(NewReviewEvent event) {
        require(event.reviewId() != null, "NEW_REVIEW без reviewId");
        require(event.orderId() != null, "NEW_REVIEW без orderId");
        require(event.courierId() != null, "NEW_REVIEW без courierId");
        require(event.createdAt() != null, "NEW_REVIEW без createdAt");
        require(event.rating() != null && event.rating() >= 1 && event.rating() <= 5,
                "NEW_REVIEW с оценкой вне диапазона 1–5: " + event.rating());

        reviewFactRepository.save(new ReviewFact(
                event.reviewId(),
                event.orderId(),
                event.courierId(),
                event.rating().shortValue(),
                event.createdAt()));
        log.info("[Statistics] Сохранён отзыв {} курьеру {}, оценка {}",
                event.reviewId(), event.courierId(), event.rating());
    }

    private void apply(CourierUpdatedEvent event) {
        require(event.courierId() != null, "COURIER_UPDATED без courierId");
        require(event.fullName() != null && !event.fullName().isBlank(), "COURIER_UPDATED без fullName");

        courierFactRepository.save(new CourierFact(event.courierId(), event.fullName().trim()));
        log.info("[Statistics] Обновлены данные курьера {}", event.courierId());
    }


    private static boolean isNewerThan(OrderFact existing, LocalDateTime incomingCompletedAt) {
        if (existing.getStatus() == null) {
            return false;
        }
        LocalDateTime existingCompletedAt = existing.getStatus() == FactOrderStatus.DELIVERED
                ? existing.getDeliveredAt()
                : existing.getCancelledAt();
        return existingCompletedAt != null && existingCompletedAt.isAfter(incomingCompletedAt);
    }

    private <T> T parse(String payload, Class<T> type) {
        if (payload == null || payload.isBlank()) {
            throw new InvalidEventException("пустое сообщение");
        }
        try {
            return jsonMapper.readValue(payload, type);
        } catch (JacksonException e) {
            throw new InvalidEventException("некорректный JSON для " + type.getSimpleName() + ": "
                    + e.getOriginalMessage(), e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidEventException(message);
        }
    }
}
