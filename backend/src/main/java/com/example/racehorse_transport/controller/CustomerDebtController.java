package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.payment.DebtDisputeDecisionRequestDto;
import com.example.racehorse_transport.dto.payment.DebtDisputeRequestDto;
import com.example.racehorse_transport.dto.payment.DebtExtensionRequestDto;
import com.example.racehorse_transport.dto.payment.PaymentResponseDto;
import com.example.racehorse_transport.service.CustomerDebtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/extra-charges")
@RequiredArgsConstructor
public class CustomerDebtController {
    private final CustomerDebtService debts;

    @PostMapping("/{id}/dispute")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<PaymentResponseDto> dispute(@PathVariable Long id, @Valid @RequestBody DebtDisputeRequestDto request,
                                                      Authentication actor) {
        return ApiResponseDto.success(debts.dispute(id, request, actor.getName()), "Đã gửi khiếu nại khoản thu.");
    }

    @PostMapping("/{id}/dispute-decision")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<PaymentResponseDto> decideDispute(@PathVariable Long id,
            @Valid @RequestBody DebtDisputeDecisionRequestDto request, Authentication actor) {
        return ApiResponseDto.success(debts.decideDispute(id, request, actor.getName()), "Đã kết luận khiếu nại.");
    }

    @PostMapping("/{id}/extend")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<PaymentResponseDto> extend(@PathVariable Long id, @Valid @RequestBody DebtExtensionRequestDto request,
                                                     Authentication actor) {
        return ApiResponseDto.success(debts.extend(id, request, actor.getName()), "Đã gia hạn.");
    }

    @PostMapping(path = "/{id}/write-off", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<PaymentResponseDto> writeOff(@PathVariable Long id,
            @Valid @RequestPart("request") DebtDisputeRequestDto request,
            @RequestPart("decision") MultipartFile decision, Authentication actor) {
        return ApiResponseDto.success(debts.writeOff(id, request, decision, actor.getName()), "Đã xóa nợ.");
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<PaymentResponseDto> reopen(@PathVariable Long id, Authentication actor) {
        return ApiResponseDto.success(debts.reopen(id, actor.getName()), "Đã mở lại khoản thu.");
    }

    @PostMapping("/{id}/release-block")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<PaymentResponseDto> releaseBlock(@PathVariable Long id, Authentication actor) {
        return ApiResponseDto.success(debts.releaseBlock(id, actor.getName()), "Đã mở quyền đặt đơn.");
    }
}
