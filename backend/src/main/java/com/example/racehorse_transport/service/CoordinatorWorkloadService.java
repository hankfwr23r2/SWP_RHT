package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.StaffWorkloadResponseDto;
import java.util.List;

public interface CoordinatorWorkloadService {
    List<StaffWorkloadResponseDto> workloads();
    void requireNoWork(Long staffId);
}
