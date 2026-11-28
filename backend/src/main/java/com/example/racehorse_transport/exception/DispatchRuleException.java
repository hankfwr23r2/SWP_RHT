package com.example.racehorse_transport.exception;

import lombok.Getter;

// Vi phạm quy tắc điều độ (lượt xe, điểm dừng, tổ lái, chặng của ngựa). conflict = xung đột với dữ liệu đang có (409).
@Getter
public class DispatchRuleException extends RuntimeException {

    private final String field;
    private final boolean conflict;

    public DispatchRuleException(String field, String message) {
        this(field, message, false);
    }

    public DispatchRuleException(String field, String message, boolean conflict) {
        super(message);
        this.field = field;
        this.conflict = conflict;
    }
}
