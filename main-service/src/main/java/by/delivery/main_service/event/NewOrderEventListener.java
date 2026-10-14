package by.delivery.main_service.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class NewOrderEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewOrder(NewOrderEvent event) {
        log.info("[Broker 1] Новый заказ #{} для зоны {}: {}", event.orderId(), event.zoneId(), event);
    }
}