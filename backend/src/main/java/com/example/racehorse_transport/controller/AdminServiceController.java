package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.admin.CategoryResponseDto;
import com.example.racehorse_transport.dto.admin.CreateServiceRequestDto;
import com.example.racehorse_transport.dto.admin.ServiceResponseDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.ServiceCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/services")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminServiceController {

    private final ServiceCatalogService serviceCatalogService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<ServiceResponseDto>>> getAllServices() {
        List<ServiceResponseDto> services = serviceCatalogService.getAllServices();
        return ResponseEntity.ok(ApiResponseDto.success(services, "Lấy danh sách dịch vụ thành công"));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponseDto<List<CategoryResponseDto>>> getAllCategories() {
        List<CategoryResponseDto> categories = serviceCatalogService.getAllCategories();
        return ResponseEntity.ok(ApiResponseDto.success(categories, "Lấy danh sách phân loại dịch vụ thành công"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<ServiceResponseDto>> getServiceById(@PathVariable Integer id) {
        ServiceResponseDto service = serviceCatalogService.getServiceById(id);
        return ResponseEntity.ok(ApiResponseDto.success(service, "Lấy thông tin dịch vụ thành công"));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<ServiceResponseDto>> createService(@Valid @RequestBody CreateServiceRequestDto request) {
        ServiceResponseDto created = serviceCatalogService.createService(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(created, "Thêm mới dịch vụ thành công"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<ServiceResponseDto>> updateService(
            @PathVariable Integer id,
            @Valid @RequestBody CreateServiceRequestDto request) {
        ServiceResponseDto updated = serviceCatalogService.updateService(id, request);
        return ResponseEntity.ok(ApiResponseDto.success(updated, "Cập nhật dịch vụ thành công"));
    }

    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<ApiResponseDto<Void>> toggleStatus(@PathVariable Integer id) {
        serviceCatalogService.toggleServiceStatus(id);
        return ResponseEntity.ok(ApiResponseDto.success(null, "Cập nhật trạng thái dịch vụ thành công"));
    }
}
