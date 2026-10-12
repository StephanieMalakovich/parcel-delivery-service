package by.delivery.main_service.repository;

import by.delivery.main_service.repository.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Integer> {
}
