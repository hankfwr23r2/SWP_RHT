package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

// Dữ liệu dùng chung khi mở app, gộp trong một request: cấu hình nghiệp vụ, danh mục địa điểm, giấy tờ bắt buộc.
// Lấy từ chính các endpoint riêng nên luôn giống hệt dữ liệu admin đang chỉnh.
@RestController
@RequestMapping("/public/bootstrap")
@RequiredArgsConstructor
public class PublicBootstrapController {

    private final PublicSystemConfigController configs;
    private final LocationController locations;
    private final DocumentRequirementController documentRequirements;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> bootstrap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configs", configs.getPublicConfigs().getBody().getData());
        data.put("locations", locations.getLocationCatalog(null, null).getBody().getData());
        data.put("documentRequirements", documentRequirements.listActive().getBody().getData());
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy dữ liệu khởi động thành công"));
    }
}
