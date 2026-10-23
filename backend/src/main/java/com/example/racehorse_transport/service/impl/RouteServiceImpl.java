package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.route.CreateRouteRequest;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.LocationRoute;
import com.example.racehorse_transport.entity.LocationRouteId;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.LocationRouteRepository;
import com.example.racehorse_transport.repository.RouteRepository;
import com.example.racehorse_transport.service.RouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RouteServiceImpl implements RouteService {

    private final RouteRepository routeRepository;
    private final LocationRouteRepository locationRouteRepository;
    private final LocationRepository locationRepository;

    @Autowired
    public RouteServiceImpl(RouteRepository routeRepository,
                            LocationRouteRepository locationRouteRepository,
                            LocationRepository locationRepository) {
        this.routeRepository = routeRepository;
        this.locationRouteRepository = locationRouteRepository;
        this.locationRepository = locationRepository;
    }

    @Override
    public List<Route> findAll() {
        return routeRepository.findAll();
    }

    @Override
    public Optional<Route> findById(Integer id) {
        return routeRepository.findById(id);
    }

    @Override
    public Route save(Route entity) {
        return routeRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        routeRepository.deleteById(id);
    }

    // =========================================================================
    // PHẦN VIỆC CỦA DEV 4: Vận hành chuyến đi (Flow 4)
    // =========================================================================

    // 1. Lấy danh sách điểm dừng của một lộ trình theo thứ tự stopOrder
    @Override
    public List<LocationRoute> getStopsByRouteId(Integer routeId) {
        if (routeId == null) {
            throw new IllegalArgumentException("routeId không được để trống!");
        }
        return locationRouteRepository.findByRouteId(routeId);
    }

    // =========================================================================
    // PHẦN VIỆC CỦA ADMIN: Quản lý tuyến đường cố định & trạm dừng
    // =========================================================================

    @Override
    @Transactional
    public Route createRoute(CreateRouteRequest request) {
        if (request == null || request.getRouteName() == null || request.getRouteName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên tuyến đường không được để trống!");
        }

        Route route = new Route();
        Integer maxId = routeRepository.findAll().stream().mapToInt(Route::getId).max().orElse(0);
        route.setId(maxId + 1);
        route.setRouteName(request.getRouteName().trim());
        route.setDescription(request.getDescription());
        route.setEstimatedTime(request.getEstimatedTime());
        route.setDistance(request.getDistance());
        route.setStatus(request.getStatus() != null && !request.getStatus().isBlank()
                ? request.getStatus().trim().toUpperCase()
                : "ACTIVE");

        Route savedRoute = routeRepository.save(route);
        saveRouteStops(savedRoute, request.getStops());
        return savedRoute;
    }

    @Override
    @Transactional
    public Route updateRoute(Integer id, CreateRouteRequest request) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tuyến đường có ID: " + id));

        if (request.getRouteName() != null && !request.getRouteName().trim().isEmpty()) {
            route.setRouteName(request.getRouteName().trim());
        }
        if (request.getDescription() != null) {
            route.setDescription(request.getDescription());
        }
        if (request.getEstimatedTime() != null) {
            route.setEstimatedTime(request.getEstimatedTime());
        }
        if (request.getDistance() != null) {
            route.setDistance(request.getDistance());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            route.setStatus(request.getStatus().trim().toUpperCase());
        }

        Route savedRoute = routeRepository.save(route);
        if (request.getStops() != null) {
            saveRouteStops(savedRoute, request.getStops());
        }
        return savedRoute;
    }

    @Override
    @Transactional
    public void deactivateRoute(Integer id) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tuyến đường có ID: " + id));
        route.setStatus("INACTIVE");
        routeRepository.save(route);
    }

    private void saveRouteStops(Route route, List<CreateRouteRequest.RouteStopItem> stops) {
        locationRouteRepository.deleteByRouteId(route.getId());
        if (stops == null || stops.isEmpty()) {
            return;
        }

        List<LocationRoute> newStops = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            CreateRouteRequest.RouteStopItem stopItem = stops.get(i);
            if (stopItem.getLocationId() == null) continue;

            Location location = locationRepository.findById(stopItem.getLocationId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy điểm dừng ID: " + stopItem.getLocationId()));

            LocationRoute lr = new LocationRoute();
            LocationRouteId lrId = new LocationRouteId();
            lrId.setRouteID(route.getId());
            lrId.setLocationID(location.getId());
            lr.setId(lrId);
            lr.setRouteID(route);
            lr.setLocationID(location);
            lr.setStopOrder(stopItem.getStopOrder() != null ? stopItem.getStopOrder() : (i + 1));
            newStops.add(lr);
        }
        locationRouteRepository.saveAll(newStops);
    }
}
