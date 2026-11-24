package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.StallTypeResponseDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.StallTypeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stall-types")
@RequiredArgsConstructor
public class StallTypeController {

    private final StallTypeService stallTypes;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponseDto<List<StallTypeResponseDto>> stallTypes() {
        return ApiResponseDto.success(stallTypes.stallTypes(), "Danh sách loại khoang");
    }
}
