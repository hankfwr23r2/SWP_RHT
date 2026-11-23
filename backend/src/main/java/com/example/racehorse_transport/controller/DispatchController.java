package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.dispatch.CrewAssignmentRequestDto;
import com.example.racehorse_transport.dto.dispatch.CrewCandidateResponseDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripCancelRequestDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripCreateRequestDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripResponseDto;
import com.example.racehorse_transport.dto.vehicle.VehicleResponseDto;
import com.example.racehorse_transport.entity.enums.CrewRole;
import com.example.racehorse_transport.service.CrewAssignmentService;
import com.example.racehorse_transport.service.DispatchService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Điều độ cho điều phối viên: lượt xe, điểm dừng, tổ lái (docs/MAN_HINH_VA_TAC_DONG.md mục 3.9)
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('COORDINATOR')")
public class DispatchController {

    private final DispatchService dispatch;
    private final CrewAssignmentService crew;

    @GetMapping("/vehicle-trips")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER')")
    public ApiResponseDto<List<VehicleTripResponseDto>> trips(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponseDto.success(dispatch.trips(from, to), "Danh sách lượt xe");
    }

    @GetMapping("/vehicle-trips/{id}")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER')")
    public ApiResponseDto<VehicleTripResponseDto> trip(@PathVariable Long id) {
        return ApiResponseDto.success(dispatch.trip(id), "Chi tiết lượt xe");
    }

    @PostMapping("/vehicle-trips")
    public ResponseEntity<ApiResponseDto<VehicleTripResponseDto>> createTrip(@Valid @RequestBody VehicleTripCreateRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(dispatch.createTrip(request), "Đã lập lượt xe"));
    }

    @PostMapping("/vehicle-trips/{id}/cancel")
    public ApiResponseDto<VehicleTripResponseDto> cancelTrip(@PathVariable Long id, @Valid @RequestBody VehicleTripCancelRequestDto request) {
        return ApiResponseDto.success(dispatch.cancelTrip(id, request), "Đã hủy lượt xe");
    }

    @PostMapping("/vehicle-trips/{id}/crew-assignments")
    public ResponseEntity<ApiResponseDto<VehicleTripResponseDto>> assignCrew(@PathVariable Long id, @Valid @RequestBody CrewAssignmentRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(crew.assign(id, request), "Đã phân công"));
    }

    @PostMapping("/crew-assignments/{id}/release")
    public ApiResponseDto<VehicleTripResponseDto> releaseCrew(@PathVariable Long id) {
        return ApiResponseDto.success(crew.release(id), "Đã gỡ nhân viên khỏi lượt xe");
    }

    @GetMapping("/dispatch/vehicles")
    public ApiResponseDto<List<VehicleResponseDto>> vehicles() {
        return ApiResponseDto.success(dispatch.vehicles(), "Danh sách xe");
    }

    @GetMapping("/dispatch/crew-candidates")
    public ApiResponseDto<List<CrewCandidateResponseDto>> crewCandidates(@RequestParam CrewRole role) {
        return ApiResponseDto.success(crew.candidates(role), "Nhân viên có thể phân công");
    }
}
