package by.delivery.main_service.service;

import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.delivery.main_service.dto.PageResponse;
import by.delivery.main_service.dto.ReviewResponse;
import by.delivery.main_service.event.NewReviewEvent;
import by.delivery.main_service.repository.CourierRepository;
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

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;

    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final CourierRepository courierRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ReviewResponse leaveReview(Integer orderId, String userId, Integer rating, String comment) {
        validateInput(orderId, userId, rating);

        Order order = orderRepository.findWithLockById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Заказ не найден: " + orderId));

        if (!Objects.equals(order.getClient().getUserId(), userId)) {
            throw new SecurityException("Заказ не принадлежит этому клиенту");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException("Оценить можно только доставленный заказ");
        }
        if (order.getCourier() == null) {
            throw new IllegalStateException("У доставленного заказа не назначен курьер");
        }
        if (reviewRepository.existsByOrder_Id(orderId)) {
            throw new IllegalStateException("Для заказа #" + orderId + " отзыв уже оставлен");
        }

        Review review = new Review();
        review.setOrder(order);
        review.setCourier(order.getCourier());
        review.setRating(rating.shortValue());
        review.setComment(comment);

        Review saved = reviewRepository.saveAndFlush(review);

        eventPublisher.publishEvent(NewReviewEvent.from(saved));
        return ReviewResponse.from(saved);
    }

    private void validateInput(Integer orderId, String userId, Integer rating) {
        if (orderId == null || userId == null) {
            throw new IllegalArgumentException("Идентификаторы заказа и пользователя обязательны");
        }
        if (rating == null || rating < MIN_RATING || rating > MAX_RATING) {
            throw new IllegalArgumentException("Оценка должна быть от " + MIN_RATING + " до " + MAX_RATING);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getCourierReviews(Integer courierId, Pageable pageable) {
        if (!courierRepository.existsById(courierId)) {
            throw new EntityNotFoundException("Курьер не найден: " + courierId);
        }
        return PageResponse.of(reviewRepository.findByCourier_Id(courierId, pageable).map(ReviewResponse::from));
    }
}
