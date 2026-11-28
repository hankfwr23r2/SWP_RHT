package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.regulation.CountryRuleCreateRequestDto;
import com.example.racehorse_transport.dto.regulation.CountryRuleResponseDto;
import com.example.racehorse_transport.dto.regulation.CountryRuleUpdateRequestDto;
import com.example.racehorse_transport.dto.regulation.RegulationPreviewRequestDto;
import com.example.racehorse_transport.dto.regulation.RegulationPreviewResponseDto;
import com.example.racehorse_transport.service.RegulationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/country-rules")
@RequiredArgsConstructor
public class CountryRuleController {

    private final RegulationService regulations;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SPECIALIST')")
    public ApiResponseDto<List<CountryRuleResponseDto>> listCountryRules() {
        return ApiResponseDto.success(regulations.listCountryRules(), "Danh sách");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SPECIALIST')")
    public ApiResponseDto<CountryRuleResponseDto> getCountryRule(@PathVariable Long id) {
        return ApiResponseDto.success(regulations.getCountryRule(id), "Chi tiết");
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponseDto<CountryRuleResponseDto> createCountryRule(@Valid @RequestBody CountryRuleCreateRequestDto request) {
        return ApiResponseDto.success(regulations.createCountryRule(request), "Đã tạo");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDto<CountryRuleResponseDto> updateCountryRule(@PathVariable Long id,
            @Valid @RequestBody CountryRuleUpdateRequestDto request) {
        return ApiResponseDto.success(regulations.updateCountryRule(id, request), "Đã cập nhật");
    }

    @PostMapping("/preview")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<RegulationPreviewResponseDto> preview(@Valid @RequestBody RegulationPreviewRequestDto request) {
        return ApiResponseDto.success(regulations.preview(request), "Quy định áp dụng");
    }
}
