package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.incident.EvidenceResponseDto;
import com.example.racehorse_transport.service.IncidentEvidenceService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// Chứng từ của sự cố (B4): biên lai, hóa đơn kèm chi phí; ảnh hiện trường kèm sự cố
@RestController
@RequiredArgsConstructor
public class IncidentEvidenceController {

    private final IncidentEvidenceService evidence;

    @PostMapping(value = "/cost-items/{id}/receipts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'COORDINATOR')")
    public ApiResponseDto<List<EvidenceResponseDto>> attachReceipt(@PathVariable Long id, @RequestPart("file") MultipartFile file, Authentication authentication) {
        return ApiResponseDto.success(evidence.attachReceipt(id, file, authentication.getName()), "Đã thêm biên lai");
    }

    @GetMapping("/cost-items/{id}/receipts")
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'COORDINATOR', 'MANAGER')")
    public ApiResponseDto<List<EvidenceResponseDto>> receipts(@PathVariable Long id) {
        return ApiResponseDto.success(evidence.receipts(id), "Biên lai của chi phí");
    }

    @PostMapping(value = "/incidents/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'COORDINATOR')")
    public ApiResponseDto<List<EvidenceResponseDto>> attachPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file,
                                                              @RequestParam(required = false) BigDecimal latitude,
                                                              @RequestParam(required = false) BigDecimal longitude,
                                                              @RequestParam(required = false) String documentType, Authentication authentication) {
        return ApiResponseDto.success(evidence.attachPhoto(id, file, latitude, longitude, documentType, authentication.getName()), "Đã thêm ảnh hiện trường");
    }

    @GetMapping("/incidents/{id}/photos")
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'COORDINATOR', 'MANAGER')")
    public ApiResponseDto<List<EvidenceResponseDto>> photos(@PathVariable Long id) {
        return ApiResponseDto.success(evidence.photos(id), "Ảnh hiện trường của sự cố");
    }
}
