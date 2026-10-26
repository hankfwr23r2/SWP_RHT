package com.example.racehorse_transport.exception;

// Dịch vụ chỉ đường không trả lời được (lỗi mạng, quá hạn mức, lỗi máy chủ) → 503
public class RoutingUnavailableException extends RuntimeException {

    public RoutingUnavailableException() {
        super("Chưa lấy được tuyến đường, thử lại sau.");
    }
}
