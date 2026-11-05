package com.example.racehorse_transport.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PaymentAmountLimits {

    public static final BigDecimal MAXIMUM_VND_AMOUNT = new BigDecimal("9999999999999999");

    private PaymentAmountLimits() {}

    public static boolean exceedsMaximum(BigDecimal amount) {
        return amount != null
                && amount.setScale(0, RoundingMode.HALF_UP).compareTo(MAXIMUM_VND_AMOUNT) > 0;
    }
}
