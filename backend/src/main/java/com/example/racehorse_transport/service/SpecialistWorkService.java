package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.specialist.SpecialistWorkResponseDto;
import com.example.racehorse_transport.dto.specialist.SpecialistWorkSummaryResponseDto;
import java.util.List;

public interface SpecialistWorkService {
    List<SpecialistWorkResponseDto> queue(String username, boolean all);
    SpecialistWorkSummaryResponseDto summary(String username);
}
