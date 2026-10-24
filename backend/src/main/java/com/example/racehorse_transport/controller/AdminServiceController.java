package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.admin.CategoryResponse;
import com.example.racehorse_transport.dto.admin.CreateServiceRequest;
import com.example.racehorse_transport.dto.admin.ServiceResponse;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.AdminServiceService;
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

    private final AdminServiceService adminServiceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ServiceResponse>>> getAllServices() {
        List<ServiceResponse> services = adminServiceService.getAllServices();
        return ResponseEntity.ok(ApiResponse.success(services, "Lấy danh sách dịch vụ thành công"));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
        List<CategoryResponse> categories = adminServiceService.getAllCategories();
        return ResponseEntity.ok(ApiResponse.success(categories, "Lấy danh sách phân loại dịch vụ thành công"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceResponse>> getServiceById(@PathVariable Integer id) {
        ServiceResponse service = adminServiceService.getServiceById(id);
        return ResponseEntity.ok(ApiResponse.success(service, "Lấy thông tin dịch vụ thành công"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ServiceResponse>> createService(@Valid @RequestBody CreateServiceRequest request) {
        ServiceResponse created = adminServiceService.createService(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Thêm mới dịch vụ thành công"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceResponse>> updateService(
            @PathVariable Integer id,
            @Valid @RequestBody CreateServiceRequest request) {
        ServiceResponse updated = adminServiceService.updateService(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật dịch vụ thành công"));
    }

    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<ApiResponse<Void>> toggleStatus(@PathVariable Integer id) {
        adminServiceService.toggleServiceStatus(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Cập nhật trạng thái dịch vụ thành công"));
    }
}
