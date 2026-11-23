package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.InsuranceDeclarationRequestDto;
import com.example.racehorse_transport.entity.BookingHorse;
import java.time.LocalDate;

public interface InsuranceService {

    void declare(BookingHorse horse, InsuranceDeclarationRequestDto request, LocalDate submittedOn, String ipAddress);
}
