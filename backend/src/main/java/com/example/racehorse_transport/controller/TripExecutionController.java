package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.operation.CrewTripResponseDto;
import com.example.racehorse_transport.dto.operation.HandoverCreateRequestDto;
import com.example.racehorse_transport.dto.operation.HandoverRefusalRequestDto;
import com.example.racehorse_transport.dto.operation.HandoverScanRequestDto;
import com.example.racehorse_transport.dto.operation.StopDepartRequestDto;
import com.example.racehorse_transport.service.DeliveryFailureService;
import com.example.racehorse_transport.service.HandoverService;
import com.example.racehorse_transport.service.TripExecutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Tổ xe thực hiện lượt xe theo từng điểm dừng (docs/MAN_HINH_VA_TAC_DONG.md mục 4.1–4.5, 6.1, 6.5)
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DRIVER', 'ESCORT')")
public class TripExecutionController {

    private final TripExecutionService execution;
    private final HandoverService handovers;
    private final DeliveryFailureService deliveryFailures;

    @GetMapping("/crew/vehicle-trips/{id}")
    public ApiResponseDto<CrewTripResponseDto> trip(@PathVariable Long id, Authentication authentication) {
        return ApiResponseDto.success(handovers.crewTrip(id, authentication.getName()), "Lượt xe");
    }

    @PostMapping("/vehicle-trips/{id}/start")
    public ApiResponseDto<CrewTripResponseDto> start(@PathVariable Long id, Authentication authentication) {
        execution.start(id, authentication.getName());
        return ApiResponseDto.success(handovers.crewTrip(id, authentication.getName()), "Xe đã xuất phát");
    }

    @PostMapping("/trip-stops/{id}/arrive")
    public ApiResponseDto<CrewTripResponseDto> arrive(@PathVariable Long id, Authentication authentication) {
        Long tripId = execution.arrive(id, authentication.getName());
        return ApiResponseDto.success(handovers.crewTrip(tripId, authentication.getName()), "Đã tới điểm dừng");
    }

    @PostMapping("/trip-stops/{id}/depart")
    public ApiResponseDto<CrewTripResponseDto> depart(@PathVariable Long id, Authentication authentication,
                                                      @Valid @RequestBody(required = false) StopDepartRequestDto request) {
        Long tripId = execution.depart(id, request == null ? null : request.earlyReason(), authentication.getName());
        return ApiResponseDto.success(handovers.crewTrip(tripId, authentication.getName()), "Đã rời điểm dừng");
    }

    @PostMapping("/vehicle-trips/{id}/complete")
    public ApiResponseDto<CrewTripResponseDto> complete(@PathVariable Long id, Authentication authentication) {
        execution.complete(id, authentication.getName());
        return ApiResponseDto.success(handovers.crewTrip(id, authentication.getName()), "Đã kết thúc lượt xe");
    }

    @PostMapping("/trip-stops/{id}/handovers")
    public ResponseEntity<ApiResponseDto<CrewTripResponseDto>> createHandover(@PathVariable Long id, Authentication authentication,
                                                                               @Valid @RequestBody HandoverCreateRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(handovers.create(id, request, authentication.getName()), "Đã mở lần giao nhận"));
    }

    @PostMapping("/handovers/{id}/horses")
    public ApiResponseDto<CrewTripResponseDto> scan(@PathVariable Long id, Authentication authentication, @Valid @RequestBody HandoverScanRequestDto request) {
        return ApiResponseDto.success(handovers.scan(id, request, authentication.getName()), "Đã quét chip");
    }

    @PostMapping(value = "/handovers/{id}/refusals", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponseDto<CrewTripResponseDto> refuse(@PathVariable Long id, Authentication authentication,
            @Valid @RequestPart HandoverRefusalRequestDto request, @RequestPart MultipartFile photo) {
        return ApiResponseDto.success(handovers.refuse(id, request, photo, authentication.getName()), "Đã ghi đón hụt");
    }

    @PostMapping("/handovers/{id}/complete")
    public ApiResponseDto<CrewTripResponseDto> completeHandover(@PathVariable Long id, Authentication authentication) {
        return ApiResponseDto.success(handovers.complete(id, authentication.getName()), "Đã hoàn tất giao nhận");
    }

    @PostMapping("/handovers/{id}/start-waiting")
    public ApiResponseDto<CrewTripResponseDto> startWaiting(@PathVariable Long id, Authentication authentication) {
        Long tripId = deliveryFailures.startWaiting(id, authentication.getName());
        return ApiResponseDto.success(handovers.crewTrip(tripId, authentication.getName()), "Đã bắt đầu chờ người nhận");
    }

    @PostMapping("/handovers/{id}/fail")
    public ApiResponseDto<CrewTripResponseDto> fail(@PathVariable Long id, Authentication authentication) {
        Long tripId = deliveryFailures.fail(id, authentication.getName());
        return ApiResponseDto.success(handovers.crewTrip(tripId, authentication.getName()), "Đã ghi nhận không gặp người nhận");
    }
}

