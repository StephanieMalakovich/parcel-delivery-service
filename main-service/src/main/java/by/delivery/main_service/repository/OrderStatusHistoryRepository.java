package by.delivery.main_service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import by.delivery.main_service.repository.entity.OrderStatus;
import by.delivery.main_service.repository.entity.OrderStatusHistory;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Integer> {

    List<OrderStatusHistory> findByOrder_IdOrderByChangedAtAscIdAsc(Integer orderId);

    Optional<OrderStatusHistory> findFirstByOrder_IdAndStatusOrderByChangedAtDescIdDesc(
            Integer orderId,
            OrderStatus status);
}
