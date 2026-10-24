package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.admin.SystemConfigResponse;
import com.example.racehorse_transport.dto.admin.UpdateSystemConfigRequest;

import java.util.List;
import java.util.Map;

public interface SystemConfigService {
    List<SystemConfigResponse> getAllConfigs();
    Map<String, List<SystemConfigResponse>> getAllConfigsGrouped();
    SystemConfigResponse getConfig(String key);
    SystemConfigResponse updateConfig(String key, UpdateSystemConfigRequest request, String updatedBy);
}
