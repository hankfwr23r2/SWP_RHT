package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.report.TripReportResponseDto;

import java.util.List;

public interface TripReportService {
    List<TripReportResponseDto> findAll();
}
