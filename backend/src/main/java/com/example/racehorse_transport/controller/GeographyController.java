package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.network.CountryResponseDto;
import com.example.racehorse_transport.dto.network.ProvinceResponseDto;
import com.example.racehorse_transport.service.LocationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Danh mục quốc gia, tỉnh cho các form. Công khai (khai báo trong SecurityConfig) vì form đăng ký cũng cần.
@RestController
@RequiredArgsConstructor
public class GeographyController {

    private final LocationService locationService;

    @GetMapping("/countries")
    public ResponseEntity<ApiResponseDto<List<CountryResponseDto>>> countries() {
        return ResponseEntity.ok(ApiResponseDto.success(locationService.countries(), "Danh sách quốc gia"));
    }

    @GetMapping("/provinces")
    public ResponseEntity<ApiResponseDto<List<ProvinceResponseDto>>> provinces(@RequestParam("country") String countryCode) {
        return ResponseEntity.ok(ApiResponseDto.success(locationService.provinces(countryCode), "Danh sách tỉnh/thành"));
    }
}
