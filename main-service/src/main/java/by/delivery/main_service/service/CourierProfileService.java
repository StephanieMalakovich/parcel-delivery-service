package by.delivery.main_service.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.delivery.main_service.dto.CourierResponse;
import by.delivery.main_service.dto.CreateCourierRequest;
import by.delivery.main_service.dto.PageResponse;
import by.delivery.main_service.dto.UpdateCourierRequest;
import by.delivery.main_service.repository.CourierRepository;
import by.delivery.main_service.repository.CourierZoneRepository;
import by.delivery.main_service.repository.ZoneRepository;
import by.delivery.main_service.repository.entity.Courier;
import by.delivery.main_service.repository.entity.CourierZone;
import by.delivery.main_service.repository.entity.Zone;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CourierProfileService {

    private final CourierRepository courierRepository;
    private final CourierZoneRepository courierZoneRepository;
    private final ZoneRepository zoneRepository;

    @Transactional(readOnly = true)
    public PageResponse<CourierResponse> getAll(Pageable pageable) {
        return PageResponse.of(courierRepository.findAll(pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public CourierResponse get(Integer courierId) {
        return toResponse(load(courierId));
    }

    @Transactional
    public CourierResponse create(CreateCourierRequest request) {
        if (courierRepository.existsByUserId(request.userId())) {
            throw new IllegalStateException("Профиль курьера для пользователя " + request.userId() + " уже существует");
        }
        List<Zone> zones = loadZones(request.zoneIds());

        Courier courier = new Courier();
        courier.setUserId(request.userId());
        courier.setFullName(request.fullName());
        courier.setPhone(request.phone());
        Courier saved = courierRepository.saveAndFlush(courier);

        zones.forEach(zone -> courierZoneRepository.save(new CourierZone(saved, zone)));
        return toResponse(saved);
    }

    @Transactional
    public CourierResponse update(Integer courierId, UpdateCourierRequest request) {
        Courier courier = load(courierId);
        List<Zone> zones = loadZones(request.zoneIds());

        courier.setFullName(request.fullName());
        courier.setPhone(request.phone());

        Set<Integer> wanted = new HashSet<>();
        zones.forEach(zone -> wanted.add(zone.getId()));

        Set<Integer> existing = new HashSet<>();
        for (CourierZone link : courierZoneRepository.findByCourier_Id(courierId)) {
            existing.add(link.getId().getZoneId());
            if (!wanted.contains(link.getId().getZoneId())) {
                courierZoneRepository.delete(link);
            }
        }
        zones.stream()
                .filter(zone -> !existing.contains(zone.getId()))
                .forEach(zone -> courierZoneRepository.save(new CourierZone(courier, zone)));

        courierZoneRepository.flush();
        return toResponse(courier);
    }

    @Transactional
    public void delete(Integer courierId) {
        courierRepository.delete(load(courierId));
        courierRepository.flush();
    }

    private Courier load(Integer courierId) {
        return courierRepository.findById(courierId)
                .orElseThrow(() -> new EntityNotFoundException("Курьер не найден: " + courierId));
    }

    private List<Zone> loadZones(Set<Integer> zoneIds) {
        if (zoneIds == null || zoneIds.isEmpty()) {
            return List.of();
        }
        return zoneIds.stream()
                .map(zoneId -> zoneRepository.findById(zoneId)
                        .orElseThrow(() -> new EntityNotFoundException("Зона не найдена: " + zoneId)))
                .toList();
    }

    private CourierResponse toResponse(Courier courier) {
        List<Integer> zoneIds = courierZoneRepository.findByCourier_Id(courier.getId()).stream()
                .map(link -> link.getId().getZoneId())
                .sorted()
                .toList();
        return CourierResponse.from(courier, zoneIds);
    }
}
