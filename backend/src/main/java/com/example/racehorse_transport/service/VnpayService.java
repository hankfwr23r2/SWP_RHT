package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.payment.VnpayCreateRequestDto;
import com.example.racehorse_transport.dto.payment.VnpayCreateResponseDto;
import com.example.racehorse_transport.dto.payment.VnpayResultResponseDto;

import java.util.Map;

public interface VnpayService {

    /**
     * Tạo giao dịch PENDING và link sang cổng VNPAY. Số tiền do backend tự tính từ báo giá / quyết toán.
     */
    VnpayCreateResponseDto createPaymentUrl(VnpayCreateRequestDto request, String username, String clientIp);

    /**
     * Xác nhận kết quả VNPAY gửi về (dùng chung cho IPN và Return URL).
     * Kiểm tra chữ ký + số tiền, idempotent: gọi lặp không áp hiệu ứng nghiệp vụ lần 2.
     */
    VnpayResultResponseDto confirm(Map<String, String> params);
}
