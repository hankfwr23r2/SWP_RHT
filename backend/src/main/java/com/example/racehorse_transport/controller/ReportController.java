package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.report.OperationsSummaryResponseDto;
import com.example.racehorse_transport.dto.report.TripReportResponseDto;
import com.example.racehorse_transport.dto.report.TripReportRowResponseDto;
import com.example.racehorse_transport.service.ReportService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Báo cáo vận hành của quản lý (B6). from, to: lượt xe có giờ bắt đầu kế hoạch trong [from, to)
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class ReportController {

    private final ReportService reports;

    @GetMapping("/reports/trips")
    public ApiResponseDto<List<TripReportRowResponseDto>> trips(@RequestParam Instant from, @RequestParam Instant to) {
        return ApiResponseDto.success(reports.trips(from, to), "Báo cáo chuyến");
    }

    @GetMapping("/reports/trips/{id}")
    public ApiResponseDto<TripReportResponseDto> trip(@PathVariable Long id) {
        return ApiResponseDto.success(reports.trip(id), "Báo cáo lượt xe");
    }

    @GetMapping("/reports/operations")
    public ApiResponseDto<OperationsSummaryResponseDto> summary(@RequestParam Instant from, @RequestParam Instant to) {
        return ApiResponseDto.success(reports.summary(from, to), "Số liệu vận hành");
    }
}
