package com.example.racehorse_transport.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripReportResponse {
    private String id;
    private String tripId;
    private String customer;
    private String route;
    private String type;
    private String horses;
    private String escort;
    private Long completedAt;
    private Long plannedAt;
    private String otd;
    private Integer otdMinutes;
    private Double incidentCost;
    private String incidentNote;
    private String totalTime;
    private List<ReportLegDto> legs;
    private List<List<Object>> health;
    private List<List<Object>> events;
    private Integer rating;
    private String feedback;
}
