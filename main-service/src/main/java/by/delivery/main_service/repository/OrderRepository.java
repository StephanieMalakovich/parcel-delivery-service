package by.delivery.main_service.repository;

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
    // Заказы конкретного клиента
    Page<Order> findByClient_Id(
            Integer clientId,
            Pageable pageable);

    // Заказы, назначенные курьеру, с определённым статусом
    Page<Order> findByCourier_IdAndStatus(
            Integer courierId,
            OrderStatus status,
            Pageable pageable);

    // Заказы определённой зоны, которые ещё не назначены курьеру
    List<Order> findByZone_IdAndStatusAndCourierIsNull(
            Integer zoneId,
            OrderStatus status);

    // Заказы с определённым статусом
    Page<Order> findByStatus(
            OrderStatus status,
            Pageable pageable);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findWithLockById(Integer id);
}
