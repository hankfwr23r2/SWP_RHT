package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.config.TransportRulesResponseDto;
import com.example.racehorse_transport.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TransportRulesController {

    private final SystemConfigService configs;

    // Không cần đăng nhập (/public/**)
    @GetMapping("/public/transport-rules")
    public ApiResponseDto<TransportRulesResponseDto> transportRules() {
        return ApiResponseDto.success(configs.transportRules(), "Quy định vận chuyển");
    }
}
