package by.delivery.main_service.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
import by.delivery.main_service.security.CurrentUser;
import by.delivery.main_service.service.CourierService;
import by.delivery.main_service.service.OrderService;
import by.delivery.main_service.service.ReviewService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CurrentUser currentUser;
    private final OrderService orderService;
    private final CourierService courierService;
    private final ReviewService reviewService;

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(
            @Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(currentUser.id(), request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public PageResponse<OrderResponse> getAll(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.getAllOrders(status, pageable);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/my")
    public PageResponse<OrderResponse> getMyOrders(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.getClientOrders(currentUser.id(), pageable);
    }

    @PreAuthorize("hasRole('COURIER')")
    @GetMapping("/assigned")
    public PageResponse<OrderResponse> getAssigned(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.getCourierOrders(currentUser.id(), status, pageable);
    }

    @PreAuthorize("hasRole('COURIER')")
    @GetMapping("/available")
    public PageResponse<OrderResponse> getAvailable(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.getAvailableOrders(currentUser.id(), pageable);
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'COURIER', 'ADMIN')")
    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable Integer id) {
        return orderService.getOrderForUser(id, currentUser.id(), currentUser.isAdmin());
    }

    @PreAuthorize("hasRole('COURIER')")
    @PostMapping("/{id}/accept")
    public OrderResponse accept(
            @PathVariable Integer id) {
        return orderService.acceptOrder(currentUser.id(), id);
    }

    @PreAuthorize("hasRole('COURIER')")
    @PatchMapping("/{id}/status")
    public OrderResponse updateStatus(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return courierService.changeStatusByCourier(id, request.status(), currentUser.id());
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(
            @PathVariable Integer id) {
        return orderService.cancelOrderByClient(currentUser.id(), id);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/{id}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse review(
            @PathVariable Integer id,
            @Valid @RequestBody CreateReviewRequest request) {
        return reviewService.leaveReview(id, currentUser.id(), request.rating(), request.comment());
    }

    @PreAuthorize("hasRole('ADMIN')")
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
