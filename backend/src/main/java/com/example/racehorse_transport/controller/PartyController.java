package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.party.InsurerResponseDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.party.PartyCreateRequestDto;
import com.example.racehorse_transport.dto.party.PartyResponseDto;
import com.example.racehorse_transport.dto.party.PartyRoleCreateRequestDto;
import com.example.racehorse_transport.dto.party.PartyRoleResponseDto;
import com.example.racehorse_transport.dto.party.PartyRoleUpdateRequestDto;
import com.example.racehorse_transport.dto.party.PartyUpdateRequestDto;
import com.example.racehorse_transport.entity.enums.PartyRoleType;
import com.example.racehorse_transport.service.PartyService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/parties")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
@RequiredArgsConstructor
public class PartyController {

    private final PartyService partyService;

    @GetMapping
    public ApiResponseDto<List<PartyResponseDto>> listParties() {
        return ApiResponseDto.success(partyService.parties(), "Danh sách đối tác");
    }

    // Khách khai bảo hiểm và chuyên viên ghi báo giá đều cần chọn hãng
    @GetMapping("/insurers")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'MANAGER', 'ADMIN', 'COORDINATOR')")
    public ApiResponseDto<List<InsurerResponseDto>> listInsurers() {
        return ApiResponseDto.success(partyService.insurers(), "Danh sách hãng bảo hiểm");
    }

    @GetMapping("/{id}")
    public ApiResponseDto<PartyResponseDto> getParty(@PathVariable Long id) {
        return ApiResponseDto.success(partyService.party(id), "Chi tiết đối tác");
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<PartyResponseDto>> createParty(@Valid @RequestBody PartyCreateRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(partyService.createParty(request), "Đã thêm đối tác"));
    }

    @PutMapping("/{id}")
    public ApiResponseDto<PartyResponseDto> updateParty(@PathVariable Long id, @Valid @RequestBody PartyUpdateRequestDto request) {
        return ApiResponseDto.success(partyService.updateParty(id, request), "Đã cập nhật đối tác");
    }

    @PostMapping("/{id}/roles")
    public ResponseEntity<ApiResponseDto<PartyRoleResponseDto>> addPartyRole(
            @PathVariable Long id, @Valid @RequestBody PartyRoleCreateRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(partyService.addRole(id, request), "Đã thêm vai trò đối tác"));
    }

    @PutMapping("/{id}/roles/{roleType}")
    public ApiResponseDto<PartyRoleResponseDto> updatePartyRole(
            @PathVariable Long id, @PathVariable PartyRoleType roleType,
            @Valid @RequestBody PartyRoleUpdateRequestDto request) {
        return ApiResponseDto.success(partyService.updateRole(id, roleType, request), "Đã cập nhật vai trò đối tác");
    }
}
