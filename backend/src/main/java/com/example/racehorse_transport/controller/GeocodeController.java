package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.geocode.GeocodeResultResponseDto;
import java.util.List;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.GeocodeService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/geocode")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class GeocodeController {
    private final GeocodeService geocode;

    @GetMapping("/search")
    public ApiResponseDto<List<GeocodeResultResponseDto>> search(
            @RequestParam @NotBlank @Size(max = 200) String q,
            @RequestParam @Pattern(regexp = "[A-Za-z]{2}") String countryCode) {
        return ApiResponseDto.success(geocode.search(q, countryCode), "OK");
    }
}
