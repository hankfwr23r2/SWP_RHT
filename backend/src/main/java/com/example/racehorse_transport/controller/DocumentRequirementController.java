package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.regulation.DocumentRequirementCreateRequestDto;
import com.example.racehorse_transport.dto.regulation.DocumentRequirementResponseDto;
import com.example.racehorse_transport.dto.regulation.DocumentRequirementUpdateRequestDto;
import com.example.racehorse_transport.service.RegulationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/country-rules/{ruleId}/document-requirements")
@RequiredArgsConstructor
public class DocumentRequirementController {

    private final RegulationService regulations;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SPECIALIST')")
    public ApiResponseDto<List<DocumentRequirementResponseDto>> listDocumentRequirements(@PathVariable Long ruleId) {
        return ApiResponseDto.success(regulations.listDocumentRequirements(ruleId), "Danh sách");
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponseDto<DocumentRequirementResponseDto> createDocumentRequirement(@PathVariable Long ruleId,
            @Valid @RequestBody DocumentRequirementCreateRequestDto request) {
        return ApiResponseDto.success(regulations.createDocumentRequirement(ruleId, request), "Đã tạo");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDto<DocumentRequirementResponseDto> updateDocumentRequirement(@PathVariable Long ruleId,
            @PathVariable Long id, @Valid @RequestBody DocumentRequirementUpdateRequestDto request) {
        return ApiResponseDto.success(regulations.updateDocumentRequirement(ruleId, id, request), "Đã cập nhật");
    }
}
