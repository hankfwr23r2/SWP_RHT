package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.HorseTypeDTO;
import com.example.racehorse_transport.dto.admin.CreateHorseTypeRequest;
import com.example.racehorse_transport.dto.admin.UpdateHorseTypeInsuredValueRequest;
import com.example.racehorse_transport.response.ApiResponse;
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
    public ResponseEntity<ApiResponse<List<HorseTypeDTO>>> getAllHorseTypes() {
        return ResponseEntity.ok(ApiResponse.success(horseTypeService.findAll(), "Lấy danh sách giống ngựa thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<HorseTypeDTO>> createHorseType(@Valid @RequestBody CreateHorseTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(horseTypeService.create(request), "Thêm mới giống ngựa thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HorseTypeDTO>> updateHorseType(
            @PathVariable Integer id,
            @Valid @RequestBody CreateHorseTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(horseTypeService.update(id, request), "Cập nhật giống ngựa thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteHorseType(@PathVariable Integer id) {
        horseTypeService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa giống ngựa thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/insured-value")
    public ResponseEntity<ApiResponse<HorseTypeDTO>> updateInsuredValue(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateHorseTypeInsuredValueRequest request) {
        return ResponseEntity.ok(ApiResponse.success(horseTypeService.updateInsuredValue(id, request), "Cập nhật giá trị bảo hiểm thành công"));
    }
}
