package com.example.racehorse_transport.dto.staff;

import java.util.Map;

public record StaffWorkSummaryResponseDto(Map<String, Long> menuCounts, Map<String, Long> filterCounts) {}
