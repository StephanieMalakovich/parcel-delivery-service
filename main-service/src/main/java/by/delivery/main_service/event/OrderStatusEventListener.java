package by.delivery.main_service.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import by.delivery.main_service.repository.entity.OrderStatus;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OrderStatusEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {

        log.info(
                "[BROKER 1] ORDER_STATUS_CHANGED: orderId={}, status={}",
                event.orderId(),
                event.status());

        log.info(
                "[SOCKET SERVICE] Would notify client userId={}, courier userId={} (previous={}) "
                        + "and admins: orderId={}, status={}",
                event.clientUserId(),
                event.courierUserId(),
                event.previousCourierUserId(),
                event.orderId(),
                event.status());

        if (event.status() == OrderStatus.DELIVERED) {
            log.info(
                    "[BROKER 2] ORDER_COMPLETED: orderId={}, "
                            + "createdAt={}, assignedAt={}, "
                            + "pickedUpAt={}, deliveredAt={}",
                    event.orderId(),
                    event.createdAt(),
                    event.assignedAt(),
                    event.pickedUpAt(),
                    event.deliveredAt());

            log.info(
                    "[STATISTICS SERVICE] Would save completed order: {}",
                    event.orderId());
        }
    }
}
