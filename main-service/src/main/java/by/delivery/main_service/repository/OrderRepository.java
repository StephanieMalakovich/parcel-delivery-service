package by.delivery.main_service.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import by.delivery.main_service.repository.entity.Order;
import by.delivery.main_service.repository.entity.OrderStatus;
import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<Order, Integer> {
    Page<Order> findByClient_Id(
            Integer clientId,
            Pageable pageable);

    Page<Order> findByCourier_IdAndStatus(
            Integer courierId,
            OrderStatus status,
            Pageable pageable);

    List<Order> findByZone_IdAndStatusAndCourierIsNull(
            Integer zoneId,
            OrderStatus status);

    Page<Order> findByCourier_Id(
            Integer courierId,
            Pageable pageable);

    Page<Order> findByStatusAndZone_IdIn(
            OrderStatus status,
            Collection<Integer> zoneIds,
            Pageable pageable);

    Page<Order> findByStatus(
            OrderStatus status,
            Pageable pageable);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findWithLockById(Integer id);
}
