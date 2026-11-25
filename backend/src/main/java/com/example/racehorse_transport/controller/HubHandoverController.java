package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.health.HorseStayResponseDto;
import com.example.racehorse_transport.dto.health.StablePartnerResponseDto;
import com.example.racehorse_transport.dto.operation.HubAppointmentRequestDto;
import com.example.racehorse_transport.dto.operation.HubCollectionTimeRequestDto;
import com.example.racehorse_transport.dto.operation.HubStablingRequestDto;
import com.example.racehorse_transport.dto.operation.HubHandoverRequestDto;
import com.example.racehorse_transport.dto.operation.HubRefusalRequestDto;
import com.example.racehorse_transport.service.HorseStayService;
import com.example.racehorse_transport.service.HubHandoverService;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;
import com.example.racehorse_transport.dto.operation.HandoverPaperResponseDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Khách tự chở tới hub hoặc tự nhận ở hub (SELF): điều phối hẹn giờ, nhận và trả ngựa tại hub (MAN_HINH 3.5c)
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('COORDINATOR')")
public class HubHandoverController {

    private final HorseStayService stays;
    private final HubHandoverService hubHandovers;

    @PostMapping("/booking-horses/{id}/hub-appointments")
    public ResponseEntity<ApiResponseDto<HorseStayResponseDto>> appoint(@PathVariable Long id, @Valid @RequestBody HubAppointmentRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(stays.planSelfDropOff(id, request.plannedStartAt()), "Đã hẹn nhận ngựa tại hub"));
    }

    @GetMapping("/horse-stays/appointments")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER')")
    public ApiResponseDto<List<HorseStayResponseDto>> appointments(@RequestParam Long locationId) {
        return ApiResponseDto.success(stays.appointmentsAt(locationId), "Lịch hẹn nhận ngựa");
    }

    // Chuồng gửi qua đêm cho tổ xe chọn khi nghỉ đêm ở trạm (STAY_IN)
    @GetMapping("/stable-partners")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'DRIVER', 'ESCORT')")
    public ApiResponseDto<List<StablePartnerResponseDto>> stablePartners() {
        return ApiResponseDto.success(stays.stablePartners(), "Chuồng gửi qua đêm");
    }

    @GetMapping("/horse-stays/{id}/handover-papers")
    public ApiResponseDto<List<HandoverPaperResponseDto>> handoverPapers(@PathVariable Long id, @RequestParam boolean receiving) {
        return ApiResponseDto.success(hubHandovers.papers(id, receiving), "Giấy tờ gốc");
    }

    @PostMapping(value = "/horse-stays/{id}/receipts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponseDto<HorseStayResponseDto> receive(@PathVariable Long id, Authentication authentication, @Valid @RequestPart("request") HubHandoverRequestDto request,
                                                     @RequestPart("photo") MultipartFile photo, @RequestPart("record") MultipartFile record) {
        return ApiResponseDto.success(hubHandovers.receive(id, request, photo, record, authentication.getName()), "Đã nhận ngựa tại hub");
    }

    @PostMapping("/horse-stays/{id}/refusals")
    public ApiResponseDto<Void> refuse(@PathVariable Long id, Authentication authentication, @Valid @RequestBody HubRefusalRequestDto request) {
        hubHandovers.refuse(id, request, authentication.getName());
        return ApiResponseDto.success(null, "Đã ghi không nhận ngựa");
    }

    @PostMapping("/horse-stays/{id}/collection-time")
    public ApiResponseDto<HorseStayResponseDto> collectionTime(@PathVariable Long id, @Valid @RequestBody HubCollectionTimeRequestDto request) {
        return ApiResponseDto.success(stays.setCollectionTime(id, request.collectAt()), "Đã hẹn giờ người nhận tới");
    }

    @PostMapping("/horse-stays/{id}/stabling")
    public ApiResponseDto<HorseStayResponseDto> stable(@PathVariable Long id, Authentication authentication, @Valid @RequestBody HubStablingRequestDto request) {
        return ApiResponseDto.success(stays.stableOverdue(id, request, authentication.getName()), "Đã chuyển sang gửi chuồng");
    }

    @PostMapping(value = "/horse-stays/{id}/deliveries", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponseDto<HorseStayResponseDto> deliver(@PathVariable Long id, Authentication authentication, @Valid @RequestPart("request") HubHandoverRequestDto request,
                                                     @RequestPart("photo") MultipartFile photo, @RequestPart("record") MultipartFile record) {
        return ApiResponseDto.success(hubHandovers.deliver(id, request, photo, record, authentication.getName()), "Đã trả ngựa cho người nhận");
    }
}
