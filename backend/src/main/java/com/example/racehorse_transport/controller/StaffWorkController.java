package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.staff.StaffWorkSummaryResponseDto;
import com.example.racehorse_transport.service.StaffWorkService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffWorkController {
    private final StaffWorkService work;

    @GetMapping("/work-summary")
    @PreAuthorize("hasAnyRole('MANAGER','COORDINATOR','SPECIALIST')")
    public ApiResponseDto<StaffWorkSummaryResponseDto> workSummary(Authentication actor) {
        return ApiResponseDto.success(work.summarize(actor.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList()), "OK");
    }
}
