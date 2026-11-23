package com.example.racehorse_transport.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class BookingRuleException extends ResponseStatusException {

    public BookingRuleException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    public static BookingRuleException conflict(String message) {
        return new BookingRuleException(HttpStatus.CONFLICT, message);
    }

    private BookingRuleException(HttpStatus status, String message) {
        super(status, message);
    }
}
