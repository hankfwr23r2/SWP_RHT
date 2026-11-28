package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.dispatch.TripGroupResponseDto;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import java.time.LocalDate;
import java.util.List;

// Gợi ý gom đơn vào lượt đón hoặc giao ở một hub trong một ngày (B1). Chỉ đọc, không lập lượt xe.
public interface TripGroupingService {

    List<TripGroupResponseDto> suggest(LocalDate date, Long hubId, VehicleTripType tripType);
}
