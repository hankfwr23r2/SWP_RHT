package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.route.LocationRouteDto;
import com.example.racehorse_transport.dto.route.RouteDto;
import com.example.racehorse_transport.entity.LocationRoute;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.RouteService;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/routes")
public class RouteController {

    private final RouteService routeService;
    private final ModelMapper modelMapper;

    @Autowired
    public RouteController(RouteService routeService, ModelMapper modelMapper) {
        this.routeService = routeService;
        this.modelMapper = modelMapper;
    }

    // 1. Lấy tất cả lộ trình
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<RouteDto>>> getAllRoutes() {
        List<Route> routes = routeService.findAll();
        List<RouteDto> dtos = routes.stream()
                .map(this::toRouteDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponseDto.success(dtos, "Lấy danh sách lộ trình thành công"));
    }

    // 2. Xem chi tiết 1 lộ trình
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<RouteDto>> getRouteById(@PathVariable Integer id) {
        Optional<Route> routeOpt = routeService.findById(id);
        if (routeOpt.isPresent()) {
            RouteDto dto = toRouteDTO(routeOpt.get());
            return ResponseEntity.ok(ApiResponseDto.success(dto, "Lấy thông tin lộ trình thành công"));
        } else {
            return ResponseEntity.status(404).body(ApiResponseDto.error("Không tìm thấy lộ trình ID: " + id));
        }
    }

    // 3. Dev 4: Lấy danh sách điểm dừng của lộ trình theo thứ tự
    @GetMapping("/{routeId}/stops")
    public ResponseEntity<ApiResponseDto<List<LocationRouteDto>>> getRouteStops(@PathVariable Integer routeId) {
        List<LocationRoute> stops = routeService.getStopsByRouteId(routeId);
        List<LocationRouteDto> dtos = stops.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponseDto.success(dtos, "Lấy danh sách điểm dừng thành công"));
    }

    // 4. Admin: Tạo mới tuyến đường cố định
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponseDto<RouteDto>> createRoute(@Valid @RequestBody com.example.racehorse_transport.dto.route.CreateRouteRequestDto request) {
        Route created = routeService.createRoute(request);
        RouteDto dto = toRouteDTO(created);
        return ResponseEntity.ok(ApiResponseDto.success(dto, "Tạo tuyến đường cố định thành công"));
    }

    // 5. Admin: Cập nhật tuyến đường cố định
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<RouteDto>> updateRoute(
            @PathVariable Integer id,
            @Valid @RequestBody com.example.racehorse_transport.dto.route.CreateRouteRequestDto request) {
        Route updated = routeService.updateRoute(id, request);
        RouteDto dto = toRouteDTO(updated);
        return ResponseEntity.ok(ApiResponseDto.success(dto, "Cập nhật tuyến đường thành công"));
    }

    // 6. Admin: Ngừng kích hoạt tuyến đường
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> deleteRoute(@PathVariable Integer id) {
        routeService.deactivateRoute(id);
        return ResponseEntity.ok(ApiResponseDto.success(null, "Ngừng kích hoạt tuyến đường thành công"));
    }

    private LocationRouteDto toDTO(LocationRoute entity) {
        LocationRouteDto dto = modelMapper.map(entity, LocationRouteDto.class);
        if (entity.getId() != null) {
            dto.setRouteID(entity.getId().getRouteID());
            dto.setLocationID(entity.getId().getLocationID());
        }
        if (entity.getLocationID() != null) {
            dto.setLocationName(entity.getLocationID().getLocationName());
            dto.setLocationType(entity.getLocationID().getLocationType());
            dto.setAddress(entity.getLocationID().getAddress());
            dto.setProvince(entity.getLocationID().getProvince());
            dto.setCountry(entity.getLocationID().getCountry());
        }
        dto.setLatitude(entity.getLatitude());
        dto.setLongitude(entity.getLongitude());
        return dto;
    }

    private RouteDto toRouteDTO(Route route) {
        RouteDto dto = modelMapper.map(route, RouteDto.class);
        List<LocationRoute> stops = routeService.getStopsByRouteId(route.getId());
        if (!stops.isEmpty()) {
            LocationRoute origin = stops.get(0);
            LocationRoute destination = stops.get(stops.size() - 1);
            dto.setOriginId(origin.getLocationID().getId());
            dto.setOriginName(origin.getLocationID().getLocationName());
            dto.setDestinationId(destination.getLocationID().getId());
            dto.setDestinationName(destination.getLocationID().getLocationName());
        }
        return dto;
    }
}
