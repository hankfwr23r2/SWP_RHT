package com.example.racehorse_transport.exception;

// Tài nguyên được yêu cầu (theo đường dẫn hoặc của người đang đăng nhập) không tồn tại: trả 404.
// Mã tham chiếu sai nằm trong dữ liệu gửi lên vẫn dùng IllegalArgumentException (400).
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
