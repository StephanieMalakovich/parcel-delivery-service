package by.delivery.main_service.service;

import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.delivery.main_service.event.NewReviewEvent;
import by.delivery.main_service.repository.OrderRepository;
import by.delivery.main_service.repository.ReviewRepository;
import by.delivery.main_service.repository.entity.Order;
import by.delivery.main_service.repository.entity.OrderStatus;
import by.delivery.main_service.repository.entity.Review;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void leaveReview(
            Integer orderId,
            Integer userId,
            Integer rating,
            String comment) {
        validateInput(orderId, userId, rating);

        Order order = orderRepository.findWithLockById(orderId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order not found: " + orderId));

        if (!Objects.equals(order.getClient().getUserId(), userId)) {
            throw new SecurityException(
                    "Order does not belong to this client");
        }

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Only delivered orders can be reviewed");
        }

        if (order.getCourier() == null) {
            throw new IllegalStateException(
                    "Delivered order has no assigned courier");
        }

        if (reviewRepository.existsByOrder_Id(orderId)) {
            throw new IllegalStateException(
                    "Review already exists for order: " + orderId);
        }

        Review review = new Review();
        review.setOrder(order);
        review.setCourier(order.getCourier());
        review.setRating(rating.shortValue());
        review.setComment(comment);

        Review savedReview = reviewRepository.saveAndFlush(review);

        NewReviewEvent event = new NewReviewEvent(
                savedReview.getId(),
                order.getId(),
                order.getCourier().getId(),
                savedReview.getRating().intValue(),
                savedReview.getComment(),
                savedReview.getCreatedAt());

        eventPublisher.publishEvent(event);
    }

    private void validateInput(
            Integer orderId,
            Integer userId,
            Integer rating) {
        if (orderId == null || userId == null) {
            throw new IllegalArgumentException(
                    "orderId and userId must not be null");
        }

        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException(
                    "Rating must be between 1 and 5");
        }
    }
}
