package by.delivery.main_service.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import by.delivery.main_service.dto.AdminOrderActionRequest;
import by.delivery.main_service.dto.CreateOrderRequest;
import by.delivery.main_service.dto.CreateReviewRequest;
import by.delivery.main_service.dto.OrderResponse;
import by.delivery.main_service.dto.PageResponse;
import by.delivery.main_service.dto.ReviewResponse;
import by.delivery.main_service.dto.UpdateOrderStatusRequest;
import by.delivery.main_service.repository.entity.OrderStatus;
import by.delivery.main_service.service.CourierService;
import by.delivery.main_service.service.OrderService;
import by.delivery.main_service.service.ReviewService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final OrderService orderService;
    private final CourierService courierService;
    private final ReviewService reviewService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(
            @RequestHeader(USER_ID_HEADER) Integer userId,
            @Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(userId, request);
    }

    @GetMapping
    public PageResponse<OrderResponse> getAll(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.getAllOrders(status, pageable);
    }

    @GetMapping("/my")
    public PageResponse<OrderResponse> getMyOrders(
            @RequestHeader(USER_ID_HEADER) Integer userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.getClientOrders(userId, pageable);
    }

    @GetMapping("/assigned")
    public PageResponse<OrderResponse> getAssigned(
            @RequestHeader(USER_ID_HEADER) Integer userId,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.getCourierOrders(userId, status, pageable);
    }

    @GetMapping("/available")
    public PageResponse<OrderResponse> getAvailable(
            @RequestHeader(USER_ID_HEADER) Integer userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.getAvailableOrders(userId, pageable);
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable Integer id) {
        return orderService.getOrder(id);
    }

    @PostMapping("/{id}/accept")
    public OrderResponse accept(
            @RequestHeader(USER_ID_HEADER) Integer userId,
            @PathVariable Integer id) {
        return orderService.acceptOrder(userId, id);
    }

    @PatchMapping("/{id}/status")
    public OrderResponse updateStatus(
            @RequestHeader(USER_ID_HEADER) Integer userId,
            @PathVariable Integer id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return courierService.changeStatusByCourier(id, request.status(), userId);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(
            @RequestHeader(USER_ID_HEADER) Integer userId,
            @PathVariable Integer id) {
        return orderService.cancelOrderByClient(userId, id);
    }

    @PostMapping("/{id}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse review(
            @RequestHeader(USER_ID_HEADER) Integer userId,
            @PathVariable Integer id,
            @Valid @RequestBody CreateReviewRequest request) {
        return reviewService.leaveReview(id, userId, request.rating(), request.comment());
    }

    @PatchMapping("/{id}")
    public OrderResponse adminAction(
            @PathVariable Integer id,
            @Valid @RequestBody AdminOrderActionRequest request) {
        return switch (request.action()) {
            case REASSIGN -> {
                if (request.courierId() == null) {
                    throw new IllegalArgumentException("Для переназначения нужно указать courierId");
                }
                yield orderService.reassignOrderByAdmin(id, request.courierId());
            }
            case CANCEL -> orderService.cancelOrderByAdmin(id);
        };
    }
}
