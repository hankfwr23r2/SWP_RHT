package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.departure.DepartureDecisionResponseDto;
import java.time.LocalDate;
import java.util.List;

// Bảng chuyến cần quyết định, xác nhận chạy, nhả chỗ đơn chưa báo giá ở mốc quyết định.
public interface DepartureDecisionService {

    List<DepartureDecisionResponseDto> decisions(LocalDate fromDate, LocalDate toDate);

    DepartureDecisionResponseDto confirmRun(Long departureId, String username);

    // Scheduler gọi. Mỗi đơn một transaction. Trả về số đơn đã nhả.
    int releaseUnconfirmed();
}
