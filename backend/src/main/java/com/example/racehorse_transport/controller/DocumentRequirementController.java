package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.DocumentRequirementRequest;
import com.example.racehorse_transport.dto.DocumentRequirementResponse;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.DocumentRequirementService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class DocumentRequirementController {
    private final DocumentRequirementService service;

    @GetMapping("/public/document-requirements")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<List<DocumentRequirementResponse>>> listActive() {
        return ResponseEntity.ok(ApiResponse.success(service.listActive(), "Lấy danh sách giấy tờ bắt buộc thành công"));
    }

    @GetMapping("/admin/document-requirements")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<DocumentRequirementResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.success(service.listAll(), "Lấy cấu hình giấy tờ thành công"));
    }

    @PostMapping("/admin/document-requirements")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DocumentRequirementResponse>> create(
            @Valid @RequestBody DocumentRequirementRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                service.create(request, authentication.getName()), "Tạo cấu hình giấy tờ thành công"));
    }

    @PutMapping("/admin/document-requirements/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DocumentRequirementResponse>> update(
            @PathVariable Integer id, @Valid @RequestBody DocumentRequirementRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                service.update(id, request, authentication.getName()), "Cập nhật cấu hình giấy tờ thành công"));
    }

    @PatchMapping("/admin/document-requirements/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DocumentRequirementResponse>> changeStatus(
            @PathVariable Integer id, @Valid @RequestBody StatusRequest request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                service.changeStatus(id, request.status(), authentication.getName()),
                "Cập nhật trạng thái giấy tờ thành công"));
    }

    public record StatusRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Pattern(regexp = "ACTIVE|INACTIVE") String status) {
    }
}
