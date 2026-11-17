package com.example.racehorse_transport.util;

/** All incident and handover waiting expenses belong to the company. */
public final class IncidentCostPolicy {
    private IncidentCostPolicy() {}

    public static void requireCompany(String payer) {
        if (payer != null && !payer.isBlank()
                && !"COMPANY".equalsIgnoreCase(payer) && !"carrier".equalsIgnoreCase(payer)) {
            throw new IllegalArgumentException("Công ty chịu toàn bộ chi phí sự cố; không được chuyển chi phí sang khách hàng.");
        }
    }
}
