package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.config.ContactResponseDto;
import com.example.racehorse_transport.dto.config.SystemConfigResponseDto;
import com.example.racehorse_transport.dto.config.SystemConfigUpdateRequestDto;
import com.example.racehorse_transport.dto.config.SystemTextConfigUpdateRequestDto;
import com.example.racehorse_transport.dto.config.TransportRulesResponseDto;
import java.util.List;

public interface SystemConfigService {

    // Tham số kiểu số nguyên. Thiếu khóa hoặc giá trị không phải số thì ném lỗi cấu hình.
    int getInt(String key);

    List<SystemConfigResponseDto> configurations();

    SystemConfigResponseDto configuration(String key);

    SystemConfigResponseDto updateConfiguration(String key, String username, SystemConfigUpdateRequestDto request);

    SystemConfigResponseDto updateText(String key, String username, SystemTextConfigUpdateRequestDto request);

    // Hotline và địa chỉ văn phòng hiện cho khách
    ContactResponseDto contact();

    // Các con số quy định vận chuyển hiện cho khách
    TransportRulesResponseDto transportRules();
}
