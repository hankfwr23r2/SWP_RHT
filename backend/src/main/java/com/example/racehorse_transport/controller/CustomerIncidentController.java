package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.incident.IncidentResponse;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.IncidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/customer/incidents")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class CustomerIncidentController {

    private final IncidentService incidentService;

    // Sự cố của mọi đơn thuộc khách đang đăng nhập, trong một lần gọi
    @GetMapping
    public ResponseEntity<ApiResponse<List<IncidentResponse>>> getMyIncidents(Authentication authentication) {
        List<IncidentResponse> list = incidentService.getCustomerIncidents(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(list, "Lấy danh sách sự cố của khách hàng thành công"));
    }
}
