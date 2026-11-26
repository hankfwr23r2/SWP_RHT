package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.specialist.SpecialistWorkResponseDto;
import com.example.racehorse_transport.dto.specialist.SpecialistWorkSummaryResponseDto;
import com.example.racehorse_transport.service.SpecialistWorkService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/specialist")
@PreAuthorize("hasRole('SPECIALIST')")
@RequiredArgsConstructor
public class SpecialistWorkController {
    private final SpecialistWorkService work;

    @GetMapping("/work-queue")
    public ApiResponseDto<List<SpecialistWorkResponseDto>> specialistWorkQueue(Authentication actor,
            @RequestParam(defaultValue = "false") boolean all) {
        return ApiResponseDto.success(work.queue(actor.getName(), all), "Thành công.");
    }

    @GetMapping("/work-summary")
    public ApiResponseDto<SpecialistWorkSummaryResponseDto> specialistWorkSummary(Authentication actor) {
        return ApiResponseDto.success(work.summary(actor.getName()), "Thành công.");
    }
}
