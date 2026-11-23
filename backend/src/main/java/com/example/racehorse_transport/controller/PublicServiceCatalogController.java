package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.admin.ServiceResponseDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.ServiceCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/public/services")
@PreAuthorize("permitAll()")
@RequiredArgsConstructor
public class PublicServiceCatalogController {

    private final ServiceCatalogService serviceCatalogService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<ServiceResponseDto>>> getActiveServices() {
        List<ServiceResponseDto> services = serviceCatalogService.getActiveServices();
        return ResponseEntity.ok(ApiResponseDto.success(services, "Lấy danh sách dịch vụ đang áp dụng thành công"));
    }
}
