package com.example.racehorse_transport.exception;

public class RegulationRuleException extends RuntimeException {

    private final String field;
    private final boolean conflict;

    public RegulationRuleException(String field, String message) {
        this(field, message, false, null);
    }

    public RegulationRuleException(String field, String message, boolean conflict, Throwable cause) {
        super(message, cause);
        this.field = field;
        this.conflict = conflict;
    }

    public String getField() {
        return field;
    }

    public boolean isConflict() {
        return conflict;
    }
}
