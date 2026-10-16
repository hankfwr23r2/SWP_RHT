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
public class ReportLegDto {
    private String title;
    private String kind;
    private String meta;
    private String route;
    private List<String> planned;
    private List<String> actual;
}
