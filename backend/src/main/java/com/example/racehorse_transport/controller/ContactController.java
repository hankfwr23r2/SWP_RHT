package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.config.ContactResponseDto;
import com.example.racehorse_transport.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ContactController {

    private final SystemConfigService configs;

    // Không cần đăng nhập (/public/**)
    @GetMapping("/public/contact")
    public ApiResponseDto<ContactResponseDto> contact() {
        return ApiResponseDto.success(configs.contact(), "Thông tin liên hệ");
    }
}
