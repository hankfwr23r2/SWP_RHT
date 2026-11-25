package com.example.racehorse_transport.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class BookingRuleException extends ResponseStatusException {

    private final String field;

    public BookingRuleException(String message) {
        this(HttpStatus.BAD_REQUEST, null, message);
    }

    public BookingRuleException(String field, String message) {
        this(HttpStatus.BAD_REQUEST, field, message);
    }

    public static BookingRuleException conflict(String message) {
        return new BookingRuleException(HttpStatus.CONFLICT, null, message);
    }

    public static BookingRuleException forbidden(String message) {
        return new BookingRuleException(HttpStatus.FORBIDDEN, null, message);
    }

    private BookingRuleException(HttpStatus status, String field, String message) {
        super(status, message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
