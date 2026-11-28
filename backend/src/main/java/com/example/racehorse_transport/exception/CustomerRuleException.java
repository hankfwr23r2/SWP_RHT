package com.example.racehorse_transport.exception;

public class CustomerRuleException extends IllegalArgumentException {

    public CustomerRuleException(String message) {
        super(message);
    }

    public static class DuplicateIdentityException extends IllegalStateException {
        public DuplicateIdentityException() {
            super("Giấy tờ này đã được đăng ký.");
        }
    }
}
