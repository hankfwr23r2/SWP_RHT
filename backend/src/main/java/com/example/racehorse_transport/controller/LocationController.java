package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.LocationDTO;
import com.example.racehorse_transport.dto.CreateRestStopRequest;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LocationDTO>>> getAllLocations(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String country) {
        List<LocationDTO> dtos = locationService.findLocations(type, country).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos, "Lấy danh sách địa điểm thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/rest-stops")
    public ResponseEntity<ApiResponse<LocationDTO>> createRestStop(
            @Valid @RequestBody CreateRestStopRequest request) {
        Location created = locationService.createRestStop(request);
        return ResponseEntity.ok(ApiResponse.success(toDTO(created), "Tạo trạm dừng thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/rest-stops/{id}")
    public ResponseEntity<ApiResponse<LocationDTO>> updateRestStop(
            @PathVariable Integer id,
            @Valid @RequestBody CreateRestStopRequest request) {
        Location updated = locationService.updateRestStop(id, request);
        return ResponseEntity.ok(ApiResponse.success(toDTO(updated), "Cập nhật trạm dừng thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/rest-stops/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRestStop(@PathVariable Integer id) {
        locationService.deleteRestStop(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa trạm dừng thành công"));
    }

    private LocationDTO toDTO(Location location) {
        return LocationDTO.builder()
                .id(location.getId())
                .locationType(location.getLocationType())
                .locationName(location.getLocationName())
                .address(location.getAddress())
                .city(location.getCity())
                .province(location.getProvince())
                .country(location.getCountry())
                .build();
    }
}
