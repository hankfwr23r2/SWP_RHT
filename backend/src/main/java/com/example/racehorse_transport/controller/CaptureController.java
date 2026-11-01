package com.example.racehorse_transport.controller;
import com.example.racehorse_transport.dto.file.CaptureEvidenceDto;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.CaptureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/files/captures") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DRIVER', 'ESCORT')")
public class CaptureController {
    private final CaptureService captureService;
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CaptureEvidenceDto>> upload(@RequestParam MultipartFile file,
            @RequestParam Integer bookingId, @RequestParam String tripId, @RequestParam String purpose,
            @RequestParam(defaultValue = "") String target, @RequestParam long capturedAt, Authentication auth) {
        String role = auth.getAuthorities().stream().anyMatch(a -> "ROLE_DRIVER".equals(a.getAuthority())) ? "DRIVER" : "ESCORT";
        return ResponseEntity.ok(ApiResponse.success(captureService.upload(file, bookingId, tripId, purpose, target, capturedAt, auth.getName(), role), "Đã lưu ảnh chụp"));
    }
}
