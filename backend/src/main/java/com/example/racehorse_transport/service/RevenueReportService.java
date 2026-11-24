package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.report.RevenueReportResponseDto;
import java.time.Instant;

// Báo cáo doanh thu của quản lý (B6): đơn có khoản cọc đầu tiên trả trong [from, to), chi phí công ty chịu đã duyệt phát sinh trong [from, to)
public interface RevenueReportService {

    RevenueReportResponseDto revenue(Instant from, Instant to);
}
