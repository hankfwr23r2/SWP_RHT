package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.admin.ServiceResponse;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.AdminServiceService;
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

    private final AdminServiceService adminServiceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ServiceResponse>>> getActiveServices() {
        List<ServiceResponse> services = adminServiceService.getActiveServices();
        return ResponseEntity.ok(ApiResponse.success(services, "Lấy danh sách dịch vụ đang áp dụng thành công"));
    }
}
