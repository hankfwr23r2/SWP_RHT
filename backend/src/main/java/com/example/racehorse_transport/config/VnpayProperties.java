package com.example.racehorse_transport.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Cấu hình cổng VNPAY (Payment API v2.1.0). Mã TMN và Hash Secret chỉ lấy từ biến môi trường, không commit.
@Getter
@Component
public class VnpayProperties {

    public static final String VERSION = "2.1.0";

    @Value("${vnpay.tmn-code:}")
    private String tmnCode;

    @Value("${vnpay.hash-secret:}")
    private String hashSecret;

    @Value("${vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String payUrl;

    // Trang kết quả phía Frontend, VNPAY chuyển trình duyệt khách về đây sau khi thanh toán
    @Value("${vnpay.return-url:http://localhost:5173/payment/vnpay-return}")
    private String returnUrl;

    // Thời gian link thanh toán còn hiệu lực
    @Value("${vnpay.expire-minutes:15}")
    private int expireMinutes;

    public boolean isConfigured() {
        return tmnCode != null && !tmnCode.isBlank() && hashSecret != null && !hashSecret.isBlank();
    }
}
