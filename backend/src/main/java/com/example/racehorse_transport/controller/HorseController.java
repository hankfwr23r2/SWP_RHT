package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.HorseDto;
import com.example.racehorse_transport.dto.HorseJourneyLogDto;
import com.example.racehorse_transport.dto.UpdateHorseLocationRequestDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.HorseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/horses")
@RequiredArgsConstructor
public class HorseController {

    private final HorseService horseService;

    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'INSPECTOR', 'MANAGER', 'ADMIN', 'COORDINATOR')")
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<HorseDto>>> getAllHorses(Authentication authentication) {
        return ResponseEntity.ok(ApiResponseDto.success(
                horseService.list(authentication.getName(), isCustomer(authentication)), "Lấy danh sách hồ sơ ngựa thành công"));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'INSPECTOR', 'MANAGER', 'ADMIN', 'COORDINATOR', 'ESCORT', 'DRIVER')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<HorseDto>> getHorseById(@PathVariable Integer id, Authentication authentication) {
        return horseService.view(id, authentication.getName(), isCustomer(authentication))
                .map(horse -> ResponseEntity.ok(ApiResponseDto.success(horse, "Lấy hồ sơ ngựa thành công")))
                .orElseGet(() -> ResponseEntity.status(404).body(ApiResponseDto.error("Không tìm thấy hồ sơ ngựa")));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponseDto<HorseDto>> createHorse(@RequestBody HorseDto request, Authentication authentication) {
        HorseDto saved = horseService.create(request, authentication.getName(), isCustomer(authentication));
        return ResponseEntity.status(201).body(ApiResponseDto.success(saved, "Tạo hồ sơ ngựa thành công"));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<HorseDto>> updateHorse(@PathVariable Integer id, @RequestBody HorseDto request, Authentication authentication) {
        HorseDto saved = horseService.update(id, request, authentication.getName(), isCustomer(authentication));
        return ResponseEntity.ok(ApiResponseDto.success(saved, "Cập nhật hồ sơ ngựa thành công"));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'MANAGER')")
    @PatchMapping("/{id}/location")
    public ResponseEntity<ApiResponseDto<HorseDto>> updateLocation(
            @PathVariable Integer id,
            @RequestBody UpdateHorseLocationRequestDto request,
            Authentication authentication) {
        HorseDto saved = horseService.updateLocation(id, request, authentication.getName(), isCustomer(authentication));
        return ResponseEntity.ok(ApiResponseDto.success(saved, "Cập nhật vị trí ngựa thành công"));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'INSPECTOR', 'MANAGER', 'ADMIN', 'COORDINATOR', 'ESCORT', 'DRIVER')")
    @GetMapping("/{id}/journey-logs")
    public ResponseEntity<ApiResponseDto<List<HorseJourneyLogDto>>> getJourneyLogs(
            @PathVariable Integer id,
            Authentication authentication) {
        List<HorseJourneyLogDto> logs = horseService.journeyLogs(id, authentication.getName(), isCustomer(authentication));
        return ResponseEntity.ok(ApiResponseDto.success(logs, "Lấy lịch sử hành trình ngựa thành công"));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> deleteHorse(@PathVariable Integer id, Authentication authentication) {
        horseService.deleteHorse(id, authentication.getName(), isCustomer(authentication));
        return ResponseEntity.ok(ApiResponseDto.success(null, "Xóa hồ sơ ngựa thành công"));
    }

    private boolean isCustomer(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }
}
