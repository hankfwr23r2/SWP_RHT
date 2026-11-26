package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.horse.HorseDocumentResponseDto;
import com.example.racehorse_transport.dto.horse.HorseDocumentUploadRequestDto;
import com.example.racehorse_transport.dto.regulation.DocumentTypeResponseDto;
import com.example.racehorse_transport.service.HorseDocumentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/horses")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class HorseDocumentController {
    private final HorseDocumentService documents;

    @GetMapping("/document-types")
    public ApiResponseDto<List<DocumentTypeResponseDto>> listHorseDocumentTypes() {
        return ApiResponseDto.success(documents.documentTypes(), "Danh sách loại giấy tờ của ngựa.");
    }

    @GetMapping("/{id}/documents")
    public ApiResponseDto<List<HorseDocumentResponseDto>> listHorseDocuments(Authentication actor, @PathVariable Long id) {
        return ApiResponseDto.success(documents.documents(actor.getName(), id), "Giấy tờ của ngựa.");
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponseDto<HorseDocumentResponseDto> uploadHorseDocument(Authentication actor, @PathVariable Long id,
            @Valid @ModelAttribute HorseDocumentUploadRequestDto request) {
        return ApiResponseDto.success(documents.upload(actor.getName(), id, request), "Đã nộp giấy tờ của ngựa.");
    }
}
