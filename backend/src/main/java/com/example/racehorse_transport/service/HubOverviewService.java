package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.hub.HubOverviewResponseDto;
import java.util.List;

public interface HubOverviewService {
    List<HubOverviewResponseDto> overview();
}
