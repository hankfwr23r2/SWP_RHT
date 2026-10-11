package com.example.racehorse_transport.service;

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
    // Dev 4: Tài xế check-in tại điểm dừng
    // ==========================================
    boolean checkInAtLocation(Integer routeId, Integer locationId, String imageUrl, String gpsCoords);
}