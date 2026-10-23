package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.LocationRouteDTO;
import com.example.racehorse_transport.dto.RouteDTO;
import com.example.racehorse_transport.entity.LocationRoute;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.response.ApiResponse;
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
    public ResponseEntity<ApiResponse<List<RouteDTO>>> getAllRoutes() {
        List<Route> routes = routeService.findAll();
        List<RouteDTO> dtos = routes.stream()
                .map(r -> modelMapper.map(r, RouteDTO.class))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos, "Lấy danh sách lộ trình thành công"));
    }

    // 2. Xem chi tiết 1 lộ trình
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RouteDTO>> getRouteById(@PathVariable Integer id) {
        Optional<Route> routeOpt = routeService.findById(id);
        if (routeOpt.isPresent()) {
            RouteDTO dto = modelMapper.map(routeOpt.get(), RouteDTO.class);
            return ResponseEntity.ok(ApiResponse.success(dto, "Lấy thông tin lộ trình thành công"));
        } else {
            return ResponseEntity.status(404).body(ApiResponse.error("Không tìm thấy lộ trình ID: " + id));
        }
    }

    // 3. Dev 4: Lấy danh sách điểm dừng của lộ trình theo thứ tự
    @GetMapping("/{routeId}/stops")
    public ResponseEntity<ApiResponse<List<LocationRouteDTO>>> getRouteStops(@PathVariable Integer routeId) {
        List<LocationRoute> stops = routeService.getStopsByRouteId(routeId);
        List<LocationRouteDTO> dtos = stops.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos, "Lấy danh sách điểm dừng thành công"));
    }

    // 4. Admin: Tạo mới tuyến đường cố định
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<RouteDTO>> createRoute(@Valid @RequestBody com.example.racehorse_transport.dto.route.CreateRouteRequest request) {
        Route created = routeService.createRoute(request);
        RouteDTO dto = modelMapper.map(created, RouteDTO.class);
        return ResponseEntity.ok(ApiResponse.success(dto, "Tạo tuyến đường cố định thành công"));
    }

    // 5. Admin: Cập nhật tuyến đường cố định
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RouteDTO>> updateRoute(
            @PathVariable Integer id,
            @Valid @RequestBody com.example.racehorse_transport.dto.route.CreateRouteRequest request) {
        Route updated = routeService.updateRoute(id, request);
        RouteDTO dto = modelMapper.map(updated, RouteDTO.class);
        return ResponseEntity.ok(ApiResponse.success(dto, "Cập nhật tuyến đường thành công"));
    }

    // 6. Admin: Ngừng kích hoạt tuyến đường
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRoute(@PathVariable Integer id) {
        routeService.deactivateRoute(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Ngừng kích hoạt tuyến đường thành công"));
    }

    private LocationRouteDTO toDTO(LocationRoute entity) {
        LocationRouteDTO dto = modelMapper.map(entity, LocationRouteDTO.class);
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
        return dto;
    }
}
