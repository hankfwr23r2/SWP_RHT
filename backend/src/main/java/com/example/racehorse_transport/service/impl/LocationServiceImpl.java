package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.LocationRequest;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.LocationChangeLog;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.LocationChangeLogRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.service.LocationService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {
    private static final String BORDER_GATE = "BorderGate";

    private final LocationRepository locationRepository;
    private final LocationChangeLogRepository changeLogRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "locations", key = "(#type == null ? '' : #type) + '_' + (#country == null ? '' : #country) + '_' + #includeInactive")
    public List<Location> findLocations(String type, String country, boolean includeInactive) {
        return locationRepository.findAll().stream()
                .filter(location -> includeInactive || "ACTIVE".equalsIgnoreCase(location.getStatus()))
                .filter(location -> type == null || type.isBlank()
                        || type.equalsIgnoreCase(location.getLocationType()))
                .filter(location -> country == null || country.isBlank()
                        || country.equalsIgnoreCase(location.getCountry()))
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = "locations", allEntries = true)
    public Location createLocation(LocationRequest request, String changedBy) {
        validateRequest(request, changedBy);
        Location location = new Location();
        location.setId(locationRepository.findMaxId() + 1);
        location.setLocationCode("LOC-" + location.getId());
        apply(location, request);
        Location saved = locationRepository.save(location);
        logChanges(saved, Map.of(), values(saved), changedBy);
        return saved;
    }

    @Override
    @Transactional
    @CacheEvict(value = "locations", allEntries = true)
    public Location updateLocation(Integer id, LocationRequest request, String changedBy) {
        validateRequest(request, changedBy);
        Location location = findLocation(id);
        Map<String, String> oldValues = values(location);
        apply(location, request);
        Location saved = locationRepository.save(location);
        logChanges(saved, oldValues, values(saved), changedBy);
        return saved;
    }

    @Override
    @Transactional
    @CacheEvict(value = "locations", allEntries = true)
    public Location changeStatus(Integer id, String status, String changedBy) {
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new IllegalArgumentException("Trạng thái địa điểm không hợp lệ.");
        }
        if (changedBy == null || changedBy.isBlank()) {
            throw new IllegalArgumentException("Không xác định được người thực hiện thay đổi.");
        }
        Location location = findLocation(id);
        String oldStatus = location.getStatus();
        location.setStatus(status);
        Location saved = locationRepository.save(location);
        if (!Objects.equals(oldStatus, status)) {
            logChange(saved, "status", oldStatus, status, changedBy);
        }
        return saved;
    }

    private Location findLocation(Integer id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa điểm có ID: " + id));
    }

    private void validateRequest(LocationRequest request, String changedBy) {
        if (request == null) {
            throw new IllegalArgumentException("Thông tin địa điểm không được để trống.");
        }
        if (changedBy == null || changedBy.isBlank()) {
            throw new IllegalArgumentException("Không xác định được người thực hiện thay đổi.");
        }
        if (BORDER_GATE.equals(request.getLocationType())) {
            if (request.getDestinationCountry() == null || request.getDestinationCountry().isBlank()) {
                throw new IllegalArgumentException("Cửa khẩu phải có quốc gia đối tác.");
            }
            if (request.getOpeningMinutes() == null || request.getClosingMinutes() == null) {
                throw new IllegalArgumentException("Cửa khẩu phải có giờ mở và giờ đóng.");
            }
            if (request.getOpeningMinutes() >= request.getClosingMinutes()) {
                throw new IllegalArgumentException("Giờ mở cửa phải sớm hơn giờ đóng cửa.");
            }
        }
    }

    private void apply(Location location, LocationRequest request) {
        location.setLocationType(request.getLocationType());
        location.setLocationName(request.getLocationName().trim());
        location.setAddress(request.getAddress().trim());
        location.setCity(request.getCity().trim());
        location.setProvince(request.getProvince().trim());
        location.setCountry(request.getCountry().trim());
        location.setLatitude(request.getLatitude());
        location.setLongitude(request.getLongitude());
        location.setStatus(request.getStatus());
        location.setOpeningMinutes(request.getOpeningMinutes());
        location.setClosingMinutes(request.getClosingMinutes());
        location.setContactPhone(request.getContactPhone() == null ? null : request.getContactPhone().trim());
        location.setDestinationCountry(request.getDestinationCountry());
    }

    private Map<String, String> values(Location location) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("locationCode", location.getLocationCode());
        values.put("locationType", location.getLocationType());
        values.put("locationName", location.getLocationName());
        values.put("address", location.getAddress());
        values.put("city", location.getCity());
        values.put("province", location.getProvince());
        values.put("country", location.getCountry());
        values.put("latitude", text(location.getLatitude()));
        values.put("longitude", text(location.getLongitude()));
        values.put("status", location.getStatus());
        values.put("openingMinutes", text(location.getOpeningMinutes()));
        values.put("closingMinutes", text(location.getClosingMinutes()));
        values.put("contactPhone", location.getContactPhone());
        values.put("destinationCountry", location.getDestinationCountry());
        return values;
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private void logChanges(Location location, Map<String, String> oldValues,
            Map<String, String> newValues, String changedBy) {
        newValues.forEach((field, newValue) -> {
            String oldValue = oldValues.get(field);
            if (!Objects.equals(oldValue, newValue)) {
                logChange(location, field, oldValue, newValue, changedBy);
            }
        });
    }

    private void logChange(Location location, String field, String oldValue,
            String newValue, String changedBy) {
        LocationChangeLog entry = new LocationChangeLog();
        entry.setLocationId(location.getId());
        entry.setFieldName(field);
        entry.setOldValue(oldValue);
        entry.setNewValue(newValue);
        entry.setChangedBy(changedBy);
        entry.setChangedAt(Instant.now());
        changeLogRepository.save(entry);
    }
}
