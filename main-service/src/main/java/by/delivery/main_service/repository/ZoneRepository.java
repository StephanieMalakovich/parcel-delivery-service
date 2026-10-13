package by.delivery.main_service.repository;

import by.delivery.main_service.repository.entity.Zone;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneRepository extends JpaRepository<Zone, Integer> {
    Optional<Zone> findByName(String name);

    boolean existsByName(String name);
}
