package by.delivery.stats_service.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import by.delivery.stats_service.repository.entity.ProcessedEvent;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
