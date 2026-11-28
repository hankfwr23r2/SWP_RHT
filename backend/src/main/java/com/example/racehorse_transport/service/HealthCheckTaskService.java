package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.health.*;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HorseHealthLog;
import com.example.racehorse_transport.entity.HorseStay;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface HealthCheckTaskService {
    HealthCheckTaskResponseDto assign(HealthCheckTaskCreateRequestDto request, String username);
    List<HealthCheckAssigneeResponseDto> assignees(Long horseId, Long tripId, Long stayId);
    List<HealthCheckTaskResponseDto> ofHorse(Long horseId, String username);
    List<HealthCheckTaskResponseDto> mine(String username);
    Map<Long, List<HealthCheckTaskResponseDto>> atHubStays(List<HorseStay> stays, String username);
    HealthCheckTaskResponseDto accept(Long id, String username);
    HealthCheckTaskResponseDto cancel(Long id, HealthCheckTaskCancelRequestDto request, String username);
    boolean mayRecordStay(Long escortId, HorseStay stay, Instant recordedAt);
    Map<Long, Handover> latestCompanyHandovers(List<Long> horseIds);
    void completeFrom(HorseHealthLog log);
    void notifyOverdue();
}
