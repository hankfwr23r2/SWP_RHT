package com.example.racehorse_transport.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportLegDto {
    private String title;
    private String kind;
    private String meta;
    private String route;
    // Mốc thời gian (epoch ms): dự kiến theo lộ trình đã chốt, thực tế theo giờ check-in; chưa có thì null
    private Long plannedStart;
    private Long plannedEnd;
    private Long actualStart;
    private Long actualEnd;
}
