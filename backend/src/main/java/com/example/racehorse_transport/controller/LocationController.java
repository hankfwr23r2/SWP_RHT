package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.LocationDTO;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationRepository locationRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LocationDTO>>> getAllLocations(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String country) {
        List<Location> locations = locationRepository.findAll();
        List<LocationDTO> dtos = locations.stream()
                .filter(l -> type == null || type.isBlank() || type.equalsIgnoreCase(l.getLocationType()))
                .filter(l -> country == null || country.isBlank() || country.equalsIgnoreCase(l.getCountry()))
                .map(l -> LocationDTO.builder()
                        .id(l.getId())
                        .locationType(l.getLocationType())
                        .locationName(l.getLocationName())
                        .address(l.getAddress())
                        .city(l.getCity())
                        .province(l.getProvince())
                        .country(l.getCountry())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos, "Lấy danh sách địa điểm thành công"));
    }
}
