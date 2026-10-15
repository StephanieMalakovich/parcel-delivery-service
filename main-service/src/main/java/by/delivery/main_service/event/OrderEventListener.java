package by.delivery.main_service.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.extern.slf4j.Slf4j;


@Component
@Slf4j
public class OrderEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewOrder(NewOrderEvent event) {
        log.info("[Broker 1] Новый заказ #{} для зоны {}: {}", event.orderId(), event.zoneId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderAccepted(OrderAcceptedEvent event) {
        log.info("[Broker 1] Заявка #{} принята курьером {}: {}", event.orderId(), event.courierId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("[Broker 2] Заказ #{} отменён ({}): {}", event.orderId(), event.cancelledBy(), event);
    }
}