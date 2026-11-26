package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.operation.CrewTripResponseDto;
import com.example.racehorse_transport.dto.operation.HandoverPaperRequestDto;
import com.example.racehorse_transport.dto.operation.HandoverPaperResponseDto;
import com.example.racehorse_transport.service.HandoverPaperService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// Biên bản giao nhận và giấy tờ gốc trong lần giao nhận (tổ xe, MAN_HINH 4.2, 4.3, 6.5)
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ESCORT')")
public class HandoverPaperController {

    private final HandoverPaperService papers;

    @PostMapping(value = "/handovers/{id}/record", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponseDto<CrewTripResponseDto> record(@PathVariable Long id, Authentication authentication, @RequestPart("file") MultipartFile file) {
        return ApiResponseDto.success(papers.attachRecord(id, file, authentication.getName()), "Đã lưu biên bản giao nhận");
    }

    @PostMapping(value = "/handovers/{id}/horses/{horseLegId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponseDto<CrewTripResponseDto> photo(@PathVariable Long id, @PathVariable Long horseLegId,
                                                      Authentication authentication, @RequestPart("file") MultipartFile file) {
        return ApiResponseDto.success(papers.attachPhoto(id, horseLegId, file, authentication.getName()), "Đã lưu ảnh tình trạng ngựa");
    }

    @GetMapping("/handovers/{id}/papers")
    public ApiResponseDto<List<HandoverPaperResponseDto>> papers(@PathVariable Long id, Authentication authentication) {
        return ApiResponseDto.success(papers.papers(id, authentication.getName()), "Giấy tờ gốc");
    }

    @PostMapping("/handovers/{id}/papers")
    public ApiResponseDto<List<HandoverPaperResponseDto>> mark(@PathVariable Long id, Authentication authentication,
                                                               @Valid @RequestBody HandoverPaperRequestDto request) {
        return ApiResponseDto.success(papers.markPaper(id, request.documentId(), request.originalMissing(), authentication.getName()), "Đã ghi giấy tờ gốc");
    }
}
