package com.example.racehorse_transport.exception;

public class AccountRuleException extends IllegalArgumentException {

    public AccountRuleException(String message) {
        super(message);
    }

    public static class InvalidStatusException extends IllegalStateException {

        public InvalidStatusException() {
            super("Trạng thái tài khoản đã thay đổi, vui lòng tải lại.");
        }
    }

    public static class DuplicateAccountException extends IllegalStateException {

        public DuplicateAccountException() {
            super("Tên đăng nhập, email hoặc mã nhân viên đã được sử dụng.");
        }
    }
}
