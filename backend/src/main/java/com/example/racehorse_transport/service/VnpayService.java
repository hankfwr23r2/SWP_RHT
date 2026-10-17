package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.payment.VnpayCreateRequest;
import com.example.racehorse_transport.dto.payment.VnpayCreateResponse;
import com.example.racehorse_transport.dto.payment.VnpayResultResponse;

import java.util.Map;

public interface VnpayService {

    /**
     * Tạo giao dịch PENDING và link sang cổng VNPAY. Số tiền do backend tự tính từ báo giá / quyết toán.
     */
    VnpayCreateResponse createPaymentUrl(VnpayCreateRequest request, String username, String clientIp);

    /**
     * Xác nhận kết quả VNPAY gửi về (dùng chung cho IPN và Return URL).
     * Kiểm tra chữ ký + số tiền, idempotent: gọi lặp không áp hiệu ứng nghiệp vụ lần 2.
     */
    VnpayResultResponse confirm(Map<String, String> params);
}
