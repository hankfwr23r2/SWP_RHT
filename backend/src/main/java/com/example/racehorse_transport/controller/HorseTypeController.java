package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.HorseTypeDto;
import com.example.racehorse_transport.dto.admin.CreateHorseTypeRequestDto;
import com.example.racehorse_transport.dto.admin.UpdateHorseTypeInsuredValueRequestDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.HorseTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/horse-types")
@RequiredArgsConstructor
public class HorseTypeController {

    private final HorseTypeService horseTypeService;

    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'INSPECTOR', 'MANAGER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<HorseTypeDto>>> getAllHorseTypes() {
        return ResponseEntity.ok(ApiResponseDto.success(horseTypeService.findAll(), "Lấy danh sách giống ngựa thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponseDto<HorseTypeDto>> createHorseType(@Valid @RequestBody CreateHorseTypeRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(horseTypeService.create(request), "Thêm mới giống ngựa thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<HorseTypeDto>> updateHorseType(
            @PathVariable Integer id,
            @Valid @RequestBody CreateHorseTypeRequestDto request) {
        return ResponseEntity.ok(ApiResponseDto.success(horseTypeService.update(id, request), "Cập nhật giống ngựa thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> deleteHorseType(@PathVariable Integer id) {
        horseTypeService.delete(id);
        return ResponseEntity.ok(ApiResponseDto.success(null, "Xóa giống ngựa thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/insured-value")
    public ResponseEntity<ApiResponseDto<HorseTypeDto>> updateInsuredValue(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateHorseTypeInsuredValueRequestDto request) {
        return ResponseEntity.ok(ApiResponseDto.success(horseTypeService.updateInsuredValue(id, request), "Cập nhật giá trị bảo hiểm thành công"));
    }
}
