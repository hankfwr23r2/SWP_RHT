package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.regulation.DocumentTypeCreateRequestDto;
import com.example.racehorse_transport.dto.regulation.DocumentTypeResponseDto;
import com.example.racehorse_transport.dto.regulation.DocumentTypeUpdateRequestDto;
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
@RequestMapping("/document-types")
@RequiredArgsConstructor
public class DocumentTypeController {

    private final RegulationService regulations;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SPECIALIST')")
    public ApiResponseDto<List<DocumentTypeResponseDto>> listDocumentTypes() {
        return ApiResponseDto.success(regulations.listDocumentTypes(), "Danh sách");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SPECIALIST')")
    public ApiResponseDto<DocumentTypeResponseDto> getDocumentType(@PathVariable Long id) {
        return ApiResponseDto.success(regulations.getDocumentType(id), "Chi tiết");
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponseDto<DocumentTypeResponseDto> createDocumentType(@Valid @RequestBody DocumentTypeCreateRequestDto request) {
        return ApiResponseDto.success(regulations.createDocumentType(request), "Đã tạo");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDto<DocumentTypeResponseDto> updateDocumentType(@PathVariable Long id,
            @Valid @RequestBody DocumentTypeUpdateRequestDto request) {
        return ApiResponseDto.success(regulations.updateDocumentType(id, request), "Đã cập nhật");
    }
}
