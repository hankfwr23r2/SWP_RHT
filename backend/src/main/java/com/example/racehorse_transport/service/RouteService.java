package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.route.CreateRouteRequest;
import com.example.racehorse_transport.entity.LocationRoute;
import com.example.racehorse_transport.entity.Route;

import java.util.List;
import java.util.Optional;

public interface RouteService {
    List<Route> findAll();
    Optional<Route> findById(Integer id);
    Route save(Route entity);
    void deleteById(Integer id);

    // ==========================================
    // Dev 4: Lấy danh sách điểm dừng của lộ trình
    // ==========================================
    List<LocationRoute> getStopsByRouteId(Integer routeId);

    // ==========================================
    // Admin: Quản lý tuyến đường cố định
    // ==========================================
    Route createRoute(CreateRouteRequest request);
    Route updateRoute(Integer id, CreateRouteRequest request);
    void deactivateRoute(Integer id);
}
