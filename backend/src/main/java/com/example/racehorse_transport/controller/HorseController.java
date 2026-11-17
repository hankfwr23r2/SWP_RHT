package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.HorseDTO;
import com.example.racehorse_transport.dto.HorseJourneyLogDTO;
import com.example.racehorse_transport.dto.UpdateHorseLocationRequest;
import com.example.racehorse_transport.response.ApiResponse;
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
    public ResponseEntity<ApiResponse<List<HorseDTO>>> getAllHorses(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                horseService.list(authentication.getName(), isCustomer(authentication)), "Lấy danh sách hồ sơ ngựa thành công"));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'INSPECTOR', 'MANAGER', 'ADMIN', 'COORDINATOR', 'ESCORT', 'DRIVER')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HorseDTO>> getHorseById(@PathVariable Integer id, Authentication authentication) {
        return horseService.view(id, authentication.getName(), isCustomer(authentication))
                .map(horse -> ResponseEntity.ok(ApiResponse.success(horse, "Lấy hồ sơ ngựa thành công")))
                .orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.error("Không tìm thấy hồ sơ ngựa")));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<HorseDTO>> createHorse(@RequestBody HorseDTO request, Authentication authentication) {
        HorseDTO saved = horseService.create(request, authentication.getName(), isCustomer(authentication));
        return ResponseEntity.status(201).body(ApiResponse.success(saved, "Tạo hồ sơ ngựa thành công"));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HorseDTO>> updateHorse(@PathVariable Integer id, @RequestBody HorseDTO request, Authentication authentication) {
        HorseDTO saved = horseService.update(id, request, authentication.getName(), isCustomer(authentication));
        return ResponseEntity.ok(ApiResponse.success(saved, "Cập nhật hồ sơ ngựa thành công"));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'MANAGER')")
    @PatchMapping("/{id}/location")
    public ResponseEntity<ApiResponse<HorseDTO>> updateLocation(
            @PathVariable Integer id,
            @RequestBody UpdateHorseLocationRequest request,
            Authentication authentication) {
        HorseDTO saved = horseService.updateLocation(id, request, authentication.getName(), isCustomer(authentication));
        return ResponseEntity.ok(ApiResponse.success(saved, "Cập nhật vị trí ngựa thành công"));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'INSPECTOR', 'MANAGER', 'ADMIN', 'COORDINATOR', 'ESCORT', 'DRIVER')")
    @GetMapping("/{id}/journey-logs")
    public ResponseEntity<ApiResponse<List<HorseJourneyLogDTO>>> getJourneyLogs(
            @PathVariable Integer id,
            Authentication authentication) {
        List<HorseJourneyLogDTO> logs = horseService.journeyLogs(id, authentication.getName(), isCustomer(authentication));
        return ResponseEntity.ok(ApiResponse.success(logs, "Lấy lịch sử hành trình ngựa thành công"));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteHorse(@PathVariable Integer id, Authentication authentication) {
        horseService.deleteHorse(id, authentication.getName(), isCustomer(authentication));
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa hồ sơ ngựa thành công"));
    }

    private boolean isCustomer(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }
}
