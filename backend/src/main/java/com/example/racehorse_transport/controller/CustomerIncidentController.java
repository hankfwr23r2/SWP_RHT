package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.incident.IncidentResponseDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
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
    public ResponseEntity<ApiResponseDto<List<IncidentResponseDto>>> getMyIncidents(Authentication authentication) {
        List<IncidentResponseDto> list = incidentService.getCustomerIncidents(authentication.getName());
        return ResponseEntity.ok(ApiResponseDto.success(list, "Lấy danh sách sự cố của khách hàng thành công"));
    }
}
