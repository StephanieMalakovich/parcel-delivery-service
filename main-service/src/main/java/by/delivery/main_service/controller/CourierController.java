package by.delivery.main_service.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import by.delivery.main_service.dto.CourierResponse;
import by.delivery.main_service.dto.CreateCourierRequest;
import by.delivery.main_service.dto.PageResponse;
import by.delivery.main_service.dto.ReviewResponse;
import by.delivery.main_service.dto.UpdateCourierRequest;
import by.delivery.main_service.service.CourierProfileService;
import by.delivery.main_service.service.ReviewService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/couriers")
@RequiredArgsConstructor
public class CourierController {

    private final CourierProfileService courierProfileService;
    private final ReviewService reviewService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public PageResponse<CourierResponse> getAll(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return courierProfileService.getAll(pageable);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public CourierResponse getById(@PathVariable Integer id) {
        return courierProfileService.get(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourierResponse create(@Valid @RequestBody CreateCourierRequest request) {
        return courierProfileService.create(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public CourierResponse update(@PathVariable Integer id, @Valid @RequestBody UpdateCourierRequest request) {
        return courierProfileService.update(id, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) {
        courierProfileService.delete(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/reviews")
    public PageResponse<ReviewResponse> getReviews(
            @PathVariable Integer id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return reviewService.getCourierReviews(id, pageable);
    }
}
