package by.delivery.main_service.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import by.delivery.main_service.repository.entity.OrderStatus;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DomainEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewOrder(NewOrderEvent event) {
        log.info("[Broker 1] Новый заказ: заказ={}, зона={}, статус={}",
                event.orderId(), event.zoneId(), event.status());
        log.info("[Socket Service] Уведомление курьерам зоны {} и администраторам: новый заказ={}",
                event.zoneId(), event.orderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderAccepted(OrderAcceptedEvent event) {
        log.info("[Broker 1] Заявка принята: заказ={}, курьер={}, зона={}",
                event.orderId(), event.courierId(), event.zoneId());
        log.info("[Socket Service] Уведомление заказчику (пользователь {}), "
                        + "остальным курьерам зоны {} и администраторам: заказ={}, курьер={}",
                event.clientUserId(), event.zoneId(), event.orderId(), event.courierId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("[Broker 1] Статус заказа изменён: заказ={}, статус={}",
                event.orderId(), event.status());
        log.info("[Socket Service] Уведомление заказчику (пользователь {}), "
                        + "курьеру (пользователь {}), прежнему курьеру (пользователь {}) и администраторам: "
                        + "заказ={}, статус={}",
                event.clientUserId(), event.courierUserId(), event.previousCourierUserId(),
                event.orderId(), event.status());

        if (event.status() == OrderStatus.DELIVERED) {
            log.info("[Broker 2] Заказ выполнен: заказ={}, создан={}, назначен={}, забран={}, доставлен={}",
                    event.orderId(), event.createdAt(), event.assignedAt(), event.pickedUpAt(), event.deliveredAt());
            log.info("[Statistics Service] Сохранение выполненного заказа: заказ={}", event.orderId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("[Broker 2] Заказ отменён: заказ={}, отменил={}, курьер={}, отменён={}",
                event.orderId(), event.cancelledBy(), event.courierId(), event.cancelledAt());
        log.info("[Statistics Service] Сохранение отменённого заказа: заказ={}", event.orderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewReview(NewReviewEvent event) {
        log.info("[Брокер 1] Новый отзыв: отзыв={}, заказ={}, оценка={}",
                event.reviewId(), event.orderId(), event.rating());
        log.info("[Socket Service] Уведомление администратору: отзыв={}, заказ={}, курьер={}, оценка={}",
                event.reviewId(), event.orderId(), event.courierId(), event.rating());
        log.info("[Брокер 2] Новый отзыв: отзыв={}, заказ={}, курьер={}, оценка={}, создан={}",
                event.reviewId(), event.orderId(), event.courierId(), event.rating(), event.createdAt());
        log.info("[Statistics Service] Сохранение отзыва: отзыв={}, заказ={}, курьер={}, оценка={}",
                event.reviewId(), event.orderId(), event.courierId(), event.rating());
    }
}
