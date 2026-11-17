package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.report.TripReportResponse;

import java.util.List;

public interface TripReportService {
    List<TripReportResponse> findAll();
}
