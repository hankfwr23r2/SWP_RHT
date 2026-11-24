package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.config.SystemConfigResponseDto;
import com.example.racehorse_transport.dto.config.SystemConfigUpdateRequestDto;
import com.example.racehorse_transport.dto.config.SystemTextConfigUpdateRequestDto;
import com.example.racehorse_transport.service.SystemConfigService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system-configs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService configs;

    @GetMapping
    public ApiResponseDto<List<SystemConfigResponseDto>> listSystemConfigs() {
        return ApiResponseDto.success(configs.configurations(), "Danh sách tham số kỹ thuật");
    }

    @GetMapping("/{key}")
    public ApiResponseDto<SystemConfigResponseDto> getSystemConfig(@PathVariable String key) {
        return ApiResponseDto.success(configs.configuration(key), "Chi tiết tham số");
    }

    @PutMapping("/{key}")
    public ApiResponseDto<SystemConfigResponseDto> updateSystemConfig(@PathVariable String key, Authentication actor,
            @Valid @RequestBody SystemConfigUpdateRequestDto request) {
        return ApiResponseDto.success(configs.updateConfiguration(key, actor.getName(), request), "Đã cập nhật tham số");
    }

    @PutMapping("/{key}/text")
    public ApiResponseDto<SystemConfigResponseDto> updateSystemTextConfig(@PathVariable String key, Authentication actor,
            @Valid @RequestBody SystemTextConfigUpdateRequestDto request) {
        return ApiResponseDto.success(configs.updateText(key, actor.getName(), request), "Đã cập nhật tham số");
    }
}
