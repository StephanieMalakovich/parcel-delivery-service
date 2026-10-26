package by.delivery.main_service.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.delivery.main_service.dto.ZoneRequest;
import by.delivery.main_service.dto.ZoneResponse;
import by.delivery.main_service.repository.ZoneRepository;
import by.delivery.main_service.repository.entity.Zone;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;

    @Transactional(readOnly = true)
    public List<ZoneResponse> getAll() {
        return zoneRepository.findAll().stream()
                .map(ZoneResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ZoneResponse get(Integer zoneId) {
        return ZoneResponse.from(load(zoneId));
    }

    @Transactional
    public ZoneResponse create(ZoneRequest request) {
        ensureNameFree(request.name(), null);

        Zone zone = new Zone();
        zone.setName(request.name());
        return ZoneResponse.from(zoneRepository.save(zone));
    }

    @Transactional
    public ZoneResponse update(Integer zoneId, ZoneRequest request) {
        Zone zone = load(zoneId);
        ensureNameFree(request.name(), zoneId);

        zone.setName(request.name());
        return ZoneResponse.from(zone);
    }

    @Transactional
    public void delete(Integer zoneId) {
        zoneRepository.delete(load(zoneId));
        zoneRepository.flush();
    }

    private Zone load(Integer zoneId) {
        return zoneRepository.findById(zoneId)
                .orElseThrow(() -> new EntityNotFoundException("Зона не найдена: " + zoneId));
    }

    private void ensureNameFree(String name, Integer currentZoneId) {
        zoneRepository.findByName(name)
                .filter(existing -> !existing.getId().equals(currentZoneId))
                .ifPresent(existing -> {
                    throw new IllegalStateException("Зона с названием «" + name + "» уже существует");
                });
    }
}
