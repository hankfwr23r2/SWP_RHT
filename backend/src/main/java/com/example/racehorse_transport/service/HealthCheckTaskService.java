package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.health.*;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HorseHealthLog;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface HealthCheckTaskService {
    HealthCheckTaskResponseDto assign(HealthCheckTaskCreateRequestDto request, String username);
    List<HealthCheckAssigneeResponseDto> assignees(Long horseId, Long tripId, Long stayId);
    List<HealthCheckTaskResponseDto> ofHorse(Long horseId, String username);
    List<HealthCheckTaskResponseDto> mine(String username);
    HealthCheckTaskResponseDto accept(Long id, String username);
    HealthCheckTaskResponseDto cancel(Long id, HealthCheckTaskCancelRequestDto request, String username);
    boolean mayRecordStay(Long escortId, Long horseId, Long stayId, Instant recordedAt);
    Map<Long, Handover> latestCompanyHandovers(List<Long> horseIds);
    void completeFrom(HorseHealthLog log);
    void notifyOverdue();
}
