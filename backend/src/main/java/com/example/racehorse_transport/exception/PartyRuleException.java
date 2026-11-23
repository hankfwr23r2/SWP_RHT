package com.example.racehorse_transport.exception;

public class PartyRuleException extends IllegalArgumentException {

    public PartyRuleException(String message) {
        super(message);
    }

    public static class DuplicateRoleException extends IllegalStateException {
        public DuplicateRoleException() {
            super("Đối tác đã có vai trò này.");
        }
    }
}
