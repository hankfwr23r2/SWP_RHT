package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.LocationChangeStatusRequest;
import com.example.racehorse_transport.dto.LocationDTO;
import com.example.racehorse_transport.dto.LocationRequest;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.LocationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/locations")
@RequiredArgsConstructor
public class LocationController {
    private final LocationService locationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LocationDTO>>> getLocationCatalog(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(ApiResponse.success(
                locationService.findLocations(type, country, true).stream()
                        .map(this::toDTO)
                        .collect(Collectors.toList()),
                "Lấy danh mục địa điểm thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<List<LocationDTO>>> getAllLocationsForAdmin(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(ApiResponse.success(
                locationService.findLocations(type, country, true).stream()
                        .map(this::toDTO)
                        .collect(Collectors.toList()),
                "Lấy danh sách địa điểm thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<LocationDTO>> createLocation(
            @Valid @RequestBody LocationRequest request,
            Authentication authentication) {
        Location created = locationService.createLocation(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toDTO(created), "Tạo địa điểm thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LocationDTO>> updateLocation(
            @PathVariable Integer id,
            @Valid @RequestBody LocationRequest request,
            Authentication authentication) {
        Location updated = locationService.updateLocation(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(toDTO(updated), "Cập nhật địa điểm thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<LocationDTO>> changeStatus(
            @PathVariable Integer id,
            @Valid @RequestBody LocationChangeStatusRequest request,
            Authentication authentication) {
        Location updated = locationService.changeStatus(id, request.getStatus(), authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(toDTO(updated), "Cập nhật trạng thái địa điểm thành công"));
    }

    private LocationDTO toDTO(Location location) {
        return LocationDTO.builder()
                .id(location.getId())
                .locationCode(location.getLocationCode())
                .locationType(location.getLocationType())
                .locationName(location.getLocationName())
                .address(location.getAddress())
                .city(location.getCity())
                .province(location.getProvince())
                .country(location.getCountry())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .status(location.getStatus())
                .openingMinutes(location.getOpeningMinutes())
                .closingMinutes(location.getClosingMinutes())
                .contactPhone(location.getContactPhone())
                .destinationCountry(location.getDestinationCountry())
                .build();
    }
}
