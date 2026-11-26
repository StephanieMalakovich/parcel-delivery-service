package by.delivery.main_service.repository;

import by.delivery.main_service.repository.entity.Courier;
import by.delivery.main_service.repository.entity.CourierStatus;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CourierRepository extends JpaRepository<Courier, Integer> {
    Optional<Courier> findByUserId(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Courier c where c.id = :id")
    Optional<Courier> findWithLockById(@Param("id") Integer id);

    boolean existsByUserId(String userId);

    List<Courier> findByStatus(CourierStatus status);
}
