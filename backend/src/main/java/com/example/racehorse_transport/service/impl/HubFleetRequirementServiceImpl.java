package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.hub.HubOverviewResponseDto.HubBranchBreakdownResponseDto;
import com.example.racehorse_transport.dto.hub.HubOverviewResponseDto.HubFleetRequirementResponseDto;
import com.example.racehorse_transport.dto.hub.HubOverviewResponseDto.HubFleetRequirementResponseDto.Item;
import com.example.racehorse_transport.dto.hub.HubOverviewResponseDto.HubStaffRoleResponseDto;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.ScheduleStatus;
import com.example.racehorse_transport.entity.enums.VehicleServiceRole;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import com.example.racehorse_transport.policy.HubFleetRequirementPolicy;
import com.example.racehorse_transport.policy.HubFleetRequirementPolicy.Requirement;
import com.example.racehorse_transport.policy.HubFleetRequirementPolicy.Rules;
import com.example.racehorse_transport.policy.HubFleetRequirementPolicy.WeeklyRun;
import com.example.racehorse_transport.repository.HubOverviewRepository;
import com.example.racehorse_transport.repository.HubOverviewRepository.FleetVehicleCount;
import com.example.racehorse_transport.service.HubFleetRequirementService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HubFleetRequirementServiceImpl implements HubFleetRequirementService {
    // Cùng múi giờ với giờ chạy khi sinh chuyến từ lịch.
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final HubOverviewRepository overview;
    private final SystemConfigService config;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<HubFleetRequirementResponseDto>> requirements(Collection<Long> hubIds, Instant asOf,
            Map<Long, List<HubStaffRoleResponseDto>> staffByHub) {
        if (hubIds.isEmpty()) return Map.of();

        LocalDate today = LocalDate.ofInstant(asOf, BUSINESS_ZONE);
        var runs = overview.fleetScheduleRuns(today, ScheduleStatus.ACTIVE, ActiveStatus.ACTIVE).stream()
                .map(row -> new WeeklyRun(row.getOriginHubId(), row.getDestinationHubId(), row.isOutbound(),
                        row.getDurationMinutes(), row.getDayOfWeek(), row.getDepartureTime(),
                        row.getVehicleCount(), row.getMaxStallCount()))
                .toList();
        var required = HubFleetRequirementPolicy.calculate(hubIds, runs, rules());
        var vehicles = overview.fleetVehicleCounts(hubIds, Set.of(VehicleStatus.ACTIVE, VehicleStatus.MAINTENANCE)).stream()
                .collect(Collectors.groupingBy(FleetVehicleCount::getHubId,
                        Collectors.toMap(FleetVehicleCount::getServiceRole, FleetVehicleCount::getVehicleCount)));
        return required.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, hub -> describe(hub.getValue(),
                vehicles.getOrDefault(hub.getKey(), Map.of()), staffByHub.getOrDefault(hub.getKey(), List.of()))));
    }

    private Rules rules() {
        return new Rules(config.getInt("LINEHAUL_REST_HOURS"), config.getInt("SPARE_VEHICLE_PERCENT"),
                config.getInt("CREW_PER_VEHICLE_PERCENT"), config.getInt("SMALL_BRANCH_VEHICLES_PER_HUB"),
                config.getInt("MIN_BRANCH_SPARE_PER_HUB"), config.getInt("BRANCH_SHARE_PERCENT"),
                config.getInt("BRANCH_VEHICLE_STALLS"), config.getInt("SPARE_HUB_MIN_PAIRS"));
    }

    private static List<HubFleetRequirementResponseDto> describe(Requirement required, Map<VehicleServiceRole, Long> vehicles,
            List<HubStaffRoleResponseDto> staff) {
        var breakdown = new HubBranchBreakdownResponseDto(required.sixSeat(), required.twoSeat(), required.branchSpare());
        return List.of(
                new HubFleetRequirementResponseDto(Item.LINEHAUL, required.linehaul(), vehicles.getOrDefault(VehicleServiceRole.LINEHAUL, 0L), null),
                new HubFleetRequirementResponseDto(Item.SPARE, required.spare(), vehicles.getOrDefault(VehicleServiceRole.SPARE, 0L), null),
                new HubFleetRequirementResponseDto(Item.BRANCH, required.branch(), vehicles.getOrDefault(VehicleServiceRole.BRANCH, 0L), breakdown),
                new HubFleetRequirementResponseDto(Item.DRIVER, required.crew(), countStaff(staff, Item.DRIVER), null),
                new HubFleetRequirementResponseDto(Item.ESCORT, required.crew(), countStaff(staff, Item.ESCORT), null));
    }

    private static long countStaff(List<HubStaffRoleResponseDto> staff, Item role) {
        return staff.stream().filter(row -> role.name().equals(row.roleCode())).mapToLong(HubStaffRoleResponseDto::count).sum();
    }
}
