package com.example.racehorse_transport.exception;

// Thử sai quá số lần cho phép trong một khoảng thời gian: trả 429, người dùng chờ rồi thử lại.
public class TooManyAttemptsException extends RuntimeException {
    public TooManyAttemptsException(String message) {
        super(message);
    }
}
