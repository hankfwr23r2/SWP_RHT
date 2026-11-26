package com.example.racehorse_transport.dto.specialist;

public record SpecialistWorkSummaryResponseDto(long verificationPending, long waitingCustomer,
        long legalPending, long insurancePending) {}
