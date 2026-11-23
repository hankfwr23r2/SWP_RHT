package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.exception.PartyRuleException;
import java.time.LocalDate;

public final class PartyRolePolicy {

    private PartyRolePolicy() {}

    public static void requireValidPeriod(LocalDate validFrom, LocalDate validTo) {
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new PartyRuleException("Ngày kết thúc phải từ ngày bắt đầu trở đi.");
        }
    }
}
