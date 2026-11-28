package com.example.racehorse_transport.exception;

// Token thiếu, sai chữ ký, hết hạn, đã thu hồi hoặc không đúng ứng dụng: trả 401, người dùng đăng nhập lại.
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
