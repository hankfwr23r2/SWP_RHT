package com.example.racehorse_transport.exception;

public class HorseRuleException extends IllegalArgumentException {

    public HorseRuleException(String message) {
        super(message);
    }

    public static class DuplicateHorseException extends IllegalStateException {
        public DuplicateHorseException() {
            super("Mã microchip đã được đăng ký.");
        }
    }
}
