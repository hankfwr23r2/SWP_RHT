package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.staff.StaffWorkSummaryResponseDto;
import java.util.Collection;

public interface StaffWorkService {
    StaffWorkSummaryResponseDto summarize(Collection<String> authorities);
}
