package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.hub.HubOverviewResponseDto.HubFleetRequirementResponseDto;
import com.example.racehorse_transport.dto.hub.HubOverviewResponseDto.HubStaffRoleResponseDto;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface HubFleetRequirementService {
    Map<Long, List<HubFleetRequirementResponseDto>> requirements(Collection<Long> hubIds, Instant asOf,
            Map<Long, List<HubStaffRoleResponseDto>> staffByHub);
}
