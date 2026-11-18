package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.admin.SystemConfigResponseDto;
import com.example.racehorse_transport.dto.admin.UpdateSystemConfigRequestDto;

import java.util.List;
import java.util.Map;

public interface SystemConfigService {
    List<SystemConfigResponseDto> getAllConfigs();
    Map<String, List<SystemConfigResponseDto>> getAllConfigsGrouped();
    SystemConfigResponseDto getConfig(String key);
    SystemConfigResponseDto updateConfig(String key, UpdateSystemConfigRequestDto request, String updatedBy);
}
