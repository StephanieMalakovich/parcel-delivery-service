package by.delivery.main_service.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class NewReviewEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewReview(NewReviewEvent event) {

        log.info(
                "[BROKER 1] NEW_REVIEW: reviewId={}, orderId={}, "
                        + "rating={}",
                event.reviewId(),
                event.orderId(),
                event.rating());

        log.info(
                "[SOCKET SERVICE] Would notify admin dashboard: "
                        + "reviewId={}, orderId={}, courierId={}, rating={}",
                event.reviewId(),
                event.orderId(),
                event.courierId(),
                event.rating());

        log.info(
                "[BROKER 2] NEW_REVIEW: reviewId={}, orderId={}, "
                        + "courierId={}, rating={}, createdAt={}",
                event.reviewId(),
                event.orderId(),
                event.courierId(),
                event.rating(),
                event.createdAt());

        log.info(
                "[STATISTICS SERVICE] Would record review: "
                        + "reviewId={}, orderId={}, courierId={}, "
                        + "rating={}, createdAt={}",
                event.reviewId(),
                event.orderId(),
                event.courierId(),
                event.rating(),
                event.createdAt());
    }
}
