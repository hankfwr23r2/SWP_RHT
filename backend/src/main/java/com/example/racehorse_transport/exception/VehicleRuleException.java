package com.example.racehorse_transport.exception;

import lombok.Getter;

@Getter
public class VehicleRuleException extends RuntimeException {

    private final String field;
    private final boolean conflict;

    public VehicleRuleException(String field, String message) {
        this(field, message, false);
    }

    public VehicleRuleException(String field, String message, boolean conflict) {
        super(message);
        this.field = field;
        this.conflict = conflict;
    }
}
