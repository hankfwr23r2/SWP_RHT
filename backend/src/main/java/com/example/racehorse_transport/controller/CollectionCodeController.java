package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.operation.CollectionCodeResponseDto;
import com.example.racehorse_transport.service.CollectionCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class CollectionCodeController {
    private final CollectionCodeService codes;

    @GetMapping("/bookings/{id}/collection-code")
    public ApiResponseDto<CollectionCodeResponseDto> code(@PathVariable Long id, Authentication authentication) {
        return ApiResponseDto.success(codes.read(id, authentication.getName()), "Mã nhận ngựa");
    }

    @PostMapping("/bookings/{id}/confirm-collection")
    public ApiResponseDto<CollectionCodeResponseDto> confirm(@PathVariable Long id, Authentication authentication) {
        return ApiResponseDto.success(codes.confirm(id, authentication.getName()), "Đã xác nhận nhận ngựa");
    }
}
