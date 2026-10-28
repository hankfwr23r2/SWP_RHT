package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.HorseTypeDTO;
import com.example.racehorse_transport.dto.admin.CreateHorseTypeRequest;
import com.example.racehorse_transport.dto.admin.UpdateHorseTypeInsuredValueRequest;
import com.example.racehorse_transport.entity.HorseType;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.repository.HorseTypeRepository;
import com.example.racehorse_transport.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/horse-types")
@RequiredArgsConstructor
public class HorseTypeController {

    private final HorseTypeRepository horseTypeRepository;
    private final HorseRepository horseRepository;

    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'INSPECTOR', 'MANAGER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<HorseTypeDTO>>> getAllHorseTypes() {
        List<HorseTypeDTO> horseTypes = horseTypeRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(horseTypes, "Lấy danh sách giống ngựa thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<HorseTypeDTO>> createHorseType(@Valid @RequestBody CreateHorseTypeRequest request) {
        if (horseTypeRepository.existsByTypeNameIgnoreCase(request.getTypeName().trim())) {
            throw new IllegalArgumentException("Giống ngựa '" + request.getTypeName().trim() + "' đã tồn tại trong hệ thống!");
        }

        HorseType horseType = new HorseType();
        horseType.setTypeName(request.getTypeName().trim());
        horseType.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        horseType.setInsuredValue(request.getInsuredValue());

        HorseType saved = horseTypeRepository.save(horseType);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toDto(saved), "Thêm mới giống ngựa thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HorseTypeDTO>> updateHorseType(
            @PathVariable Integer id,
            @Valid @RequestBody CreateHorseTypeRequest request) {
        HorseType horseType = horseTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giống ngựa ID: " + id));

        if (horseTypeRepository.existsByTypeNameIgnoreCaseAndIdNot(request.getTypeName().trim(), id)) {
            throw new IllegalArgumentException("Giống ngựa '" + request.getTypeName().trim() + "' đã được sử dụng bởi giống khác!");
        }

        horseType.setTypeName(request.getTypeName().trim());
        horseType.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        horseType.setInsuredValue(request.getInsuredValue());

        HorseType saved = horseTypeRepository.save(horseType);
        return ResponseEntity.ok(ApiResponse.success(toDto(saved), "Cập nhật giống ngựa thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteHorseType(@PathVariable Integer id) {
        HorseType horseType = horseTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giống ngựa ID: " + id));

        if (horseRepository != null && horseRepository.existsByHorseTypeID_Id(id)) {
            throw new IllegalArgumentException("Không thể xóa giống ngựa '" + horseType.getTypeName() + "' vì đang có hồ sơ ngựa trong hệ thống liên kết!");
        }

        horseTypeRepository.delete(horseType);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa giống ngựa thành công"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/insured-value")
    public ResponseEntity<ApiResponse<HorseTypeDTO>> updateInsuredValue(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateHorseTypeInsuredValueRequest request) {
        HorseType horseType = horseTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giống ngựa ID: " + id));
        horseType.setInsuredValue(request.getInsuredValue());
        HorseType saved = horseTypeRepository.save(horseType);
        return ResponseEntity.ok(ApiResponse.success(toDto(saved), "Cập nhật giá trị bảo hiểm thành công"));
    }

    private HorseTypeDTO toDto(HorseType horseType) {
        return new HorseTypeDTO(horseType.getId(), horseType.getTypeName(), horseType.getDescription(), horseType.getInsuredValue());
    }
}
