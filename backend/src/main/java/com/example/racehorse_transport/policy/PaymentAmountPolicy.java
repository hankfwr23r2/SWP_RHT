package com.example.racehorse_transport.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PaymentAmountPolicy {

    public static final BigDecimal MAXIMUM_VND_AMOUNT = new BigDecimal("9999999999999999");

    private PaymentAmountPolicy() {}

    public static boolean exceedsMaximum(BigDecimal amount) {
        return amount != null
                && amount.setScale(0, RoundingMode.HALF_UP).compareTo(MAXIMUM_VND_AMOUNT) > 0;
    }
}
