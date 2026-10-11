package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.LocationRoute;
import com.example.racehorse_transport.entity.LocationRouteId;
import com.example.racehorse_transport.entity.Log;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.repository.LocationRouteRepository;
import com.example.racehorse_transport.repository.LogRepository;
import com.example.racehorse_transport.repository.RouteRepository;
import com.example.racehorse_transport.service.RouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class RouteServiceImpl implements RouteService {

    private final RouteRepository routeRepository;
    private final LocationRouteRepository locationRouteRepository;
    private final LogRepository logRepository;

    @Autowired
    public RouteServiceImpl(RouteRepository routeRepository,
                            LocationRouteRepository locationRouteRepository,
                            LogRepository logRepository) {
        this.routeRepository = routeRepository;
        this.locationRouteRepository = locationRouteRepository;
        this.logRepository = logRepository;
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

    // 2. Tài xế check-in điểm dừng (kèm GPS / Hình ảnh)
    @Override
    @Transactional
    public boolean checkInAtLocation(Integer routeId, Integer locationId, String imageUrl, String gpsCoords) {
        // 1. Kiểm tra lộ trình có tồn tại không
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lộ trình có ID: " + routeId));

        // 2. Tìm điểm dừng tương ứng trong bảng LOCATION_ROUTE
        LocationRouteId lrId = new LocationRouteId();
        lrId.setRouteID(routeId);
        lrId.setLocationID(locationId);

        LocationRoute locationRoute = locationRouteRepository.findById(lrId)
                .orElseThrow(() -> new IllegalArgumentException("Điểm dừng không thuộc lộ trình ID: " + routeId));

        // 3. Ghi nhận thời gian đến thực tế (Giờ hiện tại)
        locationRoute.setArrivalTime(LocalTime.now());
        locationRouteRepository.save(locationRoute);

        // 4. Cập nhật trạng thái lộ trình đang chạy
        route.setStatus("IN_TRANSIT");
        routeRepository.save(route);

        // 5. Lưu bằng chứng Check-in vào bảng LOG hệ thống
        Log checkInLog = new Log();
        checkInLog.setId((int) (logRepository.count() + 1));
        checkInLog.setLogType("CHECK_IN");
        checkInLog.setStatus("SUCCESS");
        checkInLog.setNote("Tài xế check-in tại trạm. GPS: " + (gpsCoords != null ? gpsCoords : "N/A")
                + (imageUrl != null ? " | Ảnh: " + imageUrl : ""));
        checkInLog.setCreatedAt(Instant.now());
        logRepository.save(checkInLog);

        return true;
    }
}