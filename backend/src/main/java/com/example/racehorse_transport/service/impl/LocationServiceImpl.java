package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.CreateRestStopRequest;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.LocationRouteRepository;
import com.example.racehorse_transport.repository.RegulationRepository;
import com.example.racehorse_transport.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private static final String REST_STOP = "RestStop";

    private final LocationRepository locationRepository;
    private final LocationRouteRepository locationRouteRepository;
    private final RegulationRepository regulationRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "locations", key = "(#type == null ? '' : #type) + '_' + (#country == null ? '' : #country)")
    public List<Location> findLocations(String type, String country) {
        return locationRepository.findAll().stream()
                .filter(location -> type == null || type.isBlank()
                        || type.equalsIgnoreCase(location.getLocationType()))
                .filter(location -> country == null || country.isBlank()
                        || country.equalsIgnoreCase(location.getCountry()))
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = "locations", allEntries = true)
    public Location createRestStop(CreateRestStopRequest request) {
        validateRequest(request);
        Location location = new Location();
        location.setId(locationRepository.findMaxId() + 1);
        location.setLocationType(REST_STOP);
        applyDetails(location, request);
        return locationRepository.save(location);
    }

    @Override
    @Transactional
    @CacheEvict(value = "locations", allEntries = true)
    public Location updateRestStop(Integer id, CreateRestStopRequest request) {
        validateRequest(request);
        Location location = findRestStop(id);
        applyDetails(location, request);
        return locationRepository.save(location);
    }

    @Override
    @Transactional
    @CacheEvict(value = "locations", allEntries = true)
    public void deleteRestStop(Integer id) {
        Location location = findRestStop(id);
        if (locationRouteRepository.existsByLocationID_Id(id)) {
            throw new IllegalStateException("Không thể xóa trạm dừng đang được sử dụng trên tuyến đường. Hãy gỡ trạm khỏi các tuyến trước.");
        }
        if (regulationRepository.existsByLocationID_Id(id)) {
            throw new IllegalStateException("Không thể xóa trạm dừng đang được sử dụng trong quy định.");
        }
        locationRepository.delete(location);
    }

    private Location findRestStop(Integer id) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trạm dừng có ID: " + id));
        if (!REST_STOP.equalsIgnoreCase(location.getLocationType())) {
            throw new ResourceNotFoundException("Không tìm thấy trạm dừng có ID: " + id);
        }
        return location;
    }

    private void validateRequest(CreateRestStopRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Thông tin trạm dừng không được để trống.");
        }
        if (isBlank(request.getLocationName()) || isBlank(request.getAddress())
                || isBlank(request.getCity()) || isBlank(request.getProvince())
                || isBlank(request.getCountry())) {
            throw new IllegalArgumentException("Tên trạm dừng, địa chỉ, thành phố, tỉnh/thành và quốc gia không được để trống.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void applyDetails(Location location, CreateRestStopRequest request) {
        location.setLocationName(request.getLocationName().trim());
        location.setAddress(request.getAddress().trim());
        location.setCity(request.getCity().trim());
        location.setProvince(request.getProvince().trim());
        location.setCountry(request.getCountry().trim());
    }
}
