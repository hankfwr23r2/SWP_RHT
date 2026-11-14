package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.report.TripReportResponse;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.TripReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/trip-reports")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
@RequiredArgsConstructor
public class ReportController {

    private final TripReportService tripReportService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TripReportResponse>>> getTripReports() {
        return ResponseEntity.ok(ApiResponse.success(tripReportService.findAll(), "Lấy danh sách báo cáo chuyến đi thành công"));
    }
}
