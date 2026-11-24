package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.account.StaffAccountCreateRequestDto;
import com.example.racehorse_transport.dto.account.StaffAccountResponseDto;
import com.example.racehorse_transport.dto.account.StaffAccountStatusRequestDto;
import com.example.racehorse_transport.dto.account.StaffAccountUpdateRequestDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.AccountService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/staff-accounts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accounts;

    @GetMapping
    public ApiResponseDto<List<StaffAccountResponseDto>> listStaffAccounts() {
        return ApiResponseDto.success(accounts.staffAccounts(), "Danh sách nhân viên");
    }

    @GetMapping("/{id}")
    public ApiResponseDto<StaffAccountResponseDto> getStaffAccount(@PathVariable Long id) {
        return ApiResponseDto.success(accounts.staffAccount(id), "Chi tiết nhân viên");
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<StaffAccountResponseDto>> createStaffAccount(
            @Valid @RequestBody StaffAccountCreateRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(accounts.createStaffAccount(request), "Đã tạo tài khoản nhân viên"));
    }

    @PutMapping("/{id}")
    public ApiResponseDto<StaffAccountResponseDto> updateStaffAccount(
            @PathVariable Long id, @Valid @RequestBody StaffAccountUpdateRequestDto request) {
        return ApiResponseDto.success(accounts.updateStaffAccount(id, request), "Đã cập nhật hồ sơ nhân viên");
    }

    @PostMapping("/{id}/lock")
    public ApiResponseDto<StaffAccountResponseDto> lockStaffAccount(@PathVariable Long id, Authentication actor,
            @Valid @RequestBody StaffAccountStatusRequestDto request) {
        return ApiResponseDto.success(accounts.lockStaffAccount(id, actor.getName(), request), "Đã khóa tài khoản");
    }

    @PostMapping("/{id}/unlock")
    public ApiResponseDto<StaffAccountResponseDto> unlockStaffAccount(@PathVariable Long id, Authentication actor,
            @Valid @RequestBody StaffAccountStatusRequestDto request) {
        return ApiResponseDto.success(accounts.unlockStaffAccount(id, actor.getName(), request), "Đã mở khóa tài khoản");
    }
}
