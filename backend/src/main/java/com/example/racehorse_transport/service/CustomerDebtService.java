package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.payment.DebtDisputeDecisionRequestDto;
import com.example.racehorse_transport.dto.payment.DebtDisputeRequestDto;
import com.example.racehorse_transport.dto.payment.DebtExtensionRequestDto;
import com.example.racehorse_transport.dto.payment.PaymentResponseDto;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** Công nợ khoản thu thêm, tách khỏi việc đóng đơn. */
public interface CustomerDebtService {
    PaymentResponseDto dispute(Long paymentId, DebtDisputeRequestDto request, String username);

    PaymentResponseDto decideDispute(Long paymentId, DebtDisputeDecisionRequestDto request, String username);

    PaymentResponseDto extend(Long paymentId, DebtExtensionRequestDto request, String username);

    PaymentResponseDto writeOff(Long paymentId, DebtDisputeRequestDto request, MultipartFile decision, String username);

    PaymentResponseDto reopen(Long paymentId, String username);

    PaymentResponseDto releaseBlock(Long paymentId, String username);

    List<Long> followUpCandidates();

    void followUp(Long paymentId);
}
