package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.LocationChangeStatusRequestDto;
import com.example.racehorse_transport.dto.LocationDto;
import com.example.racehorse_transport.dto.LocationRequestDto;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
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
    public ResponseEntity<ApiResponseDto<List<LocationDto>>> getLocationCatalog(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(ApiResponseDto.success(
                locationService.findLocations(type, country, true).stream()
                        .map(this::toDTO)
                        .collect(Collectors.toList()),
                "Lấy danh mục địa điểm thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<ApiResponseDto<List<LocationDto>>> getAllLocationsForAdmin(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(ApiResponseDto.success(
                locationService.findLocations(type, country, true).stream()
                        .map(this::toDTO)
                        .collect(Collectors.toList()),
                "Lấy danh sách địa điểm thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponseDto<LocationDto>> createLocation(
            @Valid @RequestBody LocationRequestDto request,
            Authentication authentication) {
        Location created = locationService.createLocation(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(toDTO(created), "Tạo địa điểm thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<LocationDto>> updateLocation(
            @PathVariable Integer id,
            @Valid @RequestBody LocationRequestDto request,
            Authentication authentication) {
        Location updated = locationService.updateLocation(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponseDto.success(toDTO(updated), "Cập nhật địa điểm thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponseDto<LocationDto>> changeStatus(
            @PathVariable Integer id,
            @Valid @RequestBody LocationChangeStatusRequestDto request,
            Authentication authentication) {
        Location updated = locationService.changeStatus(id, request.getStatus(), authentication.getName());
        return ResponseEntity.ok(ApiResponseDto.success(toDTO(updated), "Cập nhật trạng thái địa điểm thành công"));
    }

    private LocationDto toDTO(Location location) {
        return LocationDto.builder()
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
