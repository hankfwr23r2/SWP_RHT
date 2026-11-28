package com.example.racehorse_transport.exception;

public class PricingRuleException extends RuntimeException {
    private final boolean conflict;

    public PricingRuleException(String message) {
        this(message, false);
    }

    public PricingRuleException(String message, boolean conflict) {
        super(message);
        this.conflict = conflict;
    }

    public boolean isConflict() {
        return conflict;
    }
}
