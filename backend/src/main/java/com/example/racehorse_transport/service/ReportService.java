package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.report.OperationsSummaryResponseDto;
import com.example.racehorse_transport.dto.report.TripReportResponseDto;
import com.example.racehorse_transport.dto.report.TripReportRowResponseDto;
import java.time.Instant;
import java.util.List;

// Báo cáo vận hành (B6): theo lượt xe, khoảng thời gian tính theo giờ bắt đầu kế hoạch
public interface ReportService {

    List<TripReportRowResponseDto> trips(Instant from, Instant to);

    TripReportResponseDto trip(Long vehicleTripId);

    OperationsSummaryResponseDto summary(Instant from, Instant to);
}
