package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.CheckInRequest;
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

    // 4. Dev 4: Tài xế check-in điểm dừng (kèm GPS / Hình ảnh)
    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<String>> checkIn(@Valid @RequestBody CheckInRequest request) {
        routeService.checkInAtLocation(
                request.getRouteId(),
                request.getLocationId(),
                request.getImageUrl(),
                request.getGpsCoords()
        );
        return ResponseEntity.ok(ApiResponse.success("Tài xế đã check-in thành công tại điểm dừng", "Thành công"));
    }

    private LocationRouteDTO toDTO(LocationRoute entity) {
        LocationRouteDTO dto = modelMapper.map(entity, LocationRouteDTO.class);
        if (entity.getId() != null) {
            dto.setRouteID(entity.getId().getRouteID());
            dto.setLocationID(entity.getId().getLocationID());
        }
        return dto;
    }
}