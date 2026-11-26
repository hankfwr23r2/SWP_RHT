package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.util.Arrays;

public final class BookingReviewPolicy {
    private BookingReviewPolicy() {}

    public static void requireStatus(BookingStatus current, BookingStatus... allowed) {
        if (!Arrays.asList(allowed).contains(current)) {
            throw BookingRuleException.conflict("Thao tác không hợp lệ. Trạng thái hiện tại của đơn là " + current + ".");
        }
    }

    public static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BookingRuleException("Phải nhập lý do.");
        }
        return reason.strip();
    }
}
