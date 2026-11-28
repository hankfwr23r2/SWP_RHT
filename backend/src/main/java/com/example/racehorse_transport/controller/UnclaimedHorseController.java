package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.operation.UnclaimedHorseResolutionRequestDto;
import com.example.racehorse_transport.service.UnclaimedHorseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class UnclaimedHorseController {
    private final UnclaimedHorseService unclaimed;

    @PostMapping(value = "/horse-stays/{id}/resolve-unclaimed", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<Void> resolve(@PathVariable Long id,
            @Valid @RequestPart("request") UnclaimedHorseResolutionRequestDto request,
            @RequestPart("proof") MultipartFile proof, Authentication actor) {
        unclaimed.resolve(id, request, proof, actor.getName());
        return ApiResponseDto.success(null, "Đã ghi nhận kết quả bàn giao.");
    }
}
