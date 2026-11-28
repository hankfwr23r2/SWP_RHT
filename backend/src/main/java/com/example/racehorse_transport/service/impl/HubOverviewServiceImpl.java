package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.hub.HubOverviewResponseDto;
import com.example.racehorse_transport.dto.hub.HubOverviewResponseDto.HubDepartureResponseDto;
import com.example.racehorse_transport.dto.hub.HubOverviewResponseDto.HubFleetResponseDto;
import com.example.racehorse_transport.dto.hub.HubOverviewResponseDto.HubHorseResponseDto;
import com.example.racehorse_transport.dto.hub.HubOverviewResponseDto.HubIncidentResponseDto;
import com.example.racehorse_transport.dto.hub.HubOverviewResponseDto.HubStaffRoleResponseDto;
import com.example.racehorse_transport.dto.hub.HubOverviewResponseDto.HubStallCountsResponseDto;
import com.example.racehorse_transport.dto.hub.HubOverviewResponseDto.HubVehicleGroupResponseDto;
import com.example.racehorse_transport.dto.hub.HubOverviewResponseDto.HubVehicleResponseDto;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.policy.DepartureDelayPolicy;
import com.example.racehorse_transport.policy.DispatchPolicy;
import com.example.racehorse_transport.repository.HubOverviewRepository;
import com.example.racehorse_transport.repository.HubOverviewRepository.HubIncident;
import com.example.racehorse_transport.repository.HubOverviewRepository.StaffCount;
import com.example.racehorse_transport.repository.HubOverviewRepository.StallCounts;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.service.HubFleetRequirementService;
import com.example.racehorse_transport.service.HubOverviewService;
import com.example.racehorse_transport.service.TripReturnService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HubOverviewServiceImpl implements HubOverviewService {
    private final LocationRepository locations;
    private final HubOverviewRepository overview;
    private final VehicleRepository vehicles;
    private final TripStopRepository stops;
    private final TripReturnService returns;
    private final HubFleetRequirementService fleetRequirements;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<HubOverviewResponseDto> overview() {
        var hubs = locations.search(LocationType.HUB, ActiveStatus.ACTIVE);
        if (hubs.isEmpty()) return List.of();

        List<Long> ids = hubs.stream().map(Location::getId).toList();
        Instant now = clock.instant();
        var horses = groupByHub(overview.openStays(ids), stay -> stay.getLocation().getId(), HubHorseResponseDto::from);
        var stalls = overview.stallCounts(ids).stream().collect(Collectors.toMap(StallCounts::getHubId,
                counts -> new HubStallCountsResponseDto(counts.getTotal(), counts.getAvailable())));
        var staff = groupByHub(overview.staffCounts(ids, EmploymentStatus.ACTIVE), StaffCount::getHubId,
                row -> new HubStaffRoleResponseDto(row.getRoleCode(), row.getStaffCount()));
        var upcoming = overview.departures(ids, now, now.plus(7, ChronoUnit.DAYS));
        var arrivals = groupByHub(upcoming, departure -> departure.getRoute().getDestination().getId(), HubOverviewServiceImpl::scheduled);
        var departures = groupByHub(upcoming, departure -> departure.getRoute().getOrigin().getId(), HubOverviewServiceImpl::scheduled);
        var incidents = groupByHub(overview.openIncidents(ids, IncidentStatus.CLOSED), HubIncident::getHubId,
                row -> HubIncidentResponseDto.from(row.getIncident()));
        var fleet = fleetByHub();
        var requirements = fleetRequirements.requirements(ids, now, staff);

        return hubs.stream().sorted(Comparator.comparing(Location::isHeadquarters).reversed()).map(hub -> {
            Long id = hub.getId();
            var hubHorses = horses.getOrDefault(id, List.of());
            var hubIncidents = incidents.getOrDefault(id, List.of());
            return HubOverviewResponseDto.from(hub, hubHorses.size(), hubHorses,
                    stalls.getOrDefault(id, new HubStallCountsResponseDto(0, 0)), fleet.at(id), staff.getOrDefault(id, List.of()),
                    arrivals.getOrDefault(id, List.of()), departures.getOrDefault(id, List.of()), hubIncidents.size(), hubIncidents,
                    requirements.get(id));
        }).toList();
    }

    private FleetByHub fleetByHub() {
        var trips = overview.currentVehicleTrips(VehicleTripStatus.IN_PROGRESS, VehicleTripStatus.COMPLETED);
        var running = trips.stream().filter(trip -> trip.getStatus() == VehicleTripStatus.IN_PROGRESS).toList();
        var lastCompleted = trips.stream().filter(trip -> trip.getStatus() == VehicleTripStatus.COMPLETED)
                .collect(Collectors.toMap(trip -> trip.getVehicle().getId(), Function.identity()));
        Set<Long> runningVehicles = running.stream().map(trip -> trip.getVehicle().getId()).collect(Collectors.toSet());
        var parked = vehicles.findAllByOrderByLicensePlateAsc();
        var idle = parked.stream().filter(vehicle -> vehicle.getStatus() == VehicleStatus.ACTIVE
                && !runningVehicles.contains(vehicle.getId())).toList();
        var maintenance = parked.stream().filter(vehicle -> vehicle.getStatus() == VehicleStatus.MAINTENANCE).toList();
        Function<Vehicle, Long> position = vehicle -> DispatchPolicy.endLocationOrHome(vehicle, lastCompleted.get(vehicle.getId())).getId();
        var returning = returningTrips(running);
        return new FleetByHub(groupByHub(idle, position, vehicle -> HubVehicleResponseDto.from(vehicle, null)),
                groupByHub(running, trip -> trip.getStartLocation().getId(), HubOverviewServiceImpl::onTrip),
                groupByHub(returning, trip -> trip.getEndLocation().getId(), HubOverviewServiceImpl::onTrip),
                groupByHub(maintenance, position, vehicle -> HubVehicleResponseDto.from(vehicle, null)));
    }

    private List<VehicleTrip> returningTrips(List<VehicleTrip> running) {
        if (running.isEmpty()) return List.of();
        var tripIds = running.stream().map(VehicleTrip::getId).toList();
        var stopsByTrip = stops.findByVehicleTrip_IdInOrderByVehicleTrip_IdAscSequenceNumberAsc(tripIds).stream()
                .collect(Collectors.groupingBy(stop -> stop.getVehicleTrip().getId()));
        var progress = returns.progress(running, stopsByTrip);
        return running.stream().filter(trip -> progress.get(trip.getId()).returning()).toList();
    }

    private static HubVehicleResponseDto onTrip(VehicleTrip trip) {
        return HubVehicleResponseDto.from(trip.getVehicle(), trip.getCode());
    }

    private static HubDepartureResponseDto scheduled(Departure departure) {
        return HubDepartureResponseDto.from(departure, DepartureDelayPolicy.effectiveTime(departure));
    }

    private static <T, R> Map<Long, List<R>> groupByHub(List<T> rows, Function<T, Long> hubId, Function<T, R> describe) {
        return rows.stream().collect(Collectors.groupingBy(hubId, Collectors.mapping(describe, Collectors.toList())));
    }

    private record FleetByHub(Map<Long, List<HubVehicleResponseDto>> idle, Map<Long, List<HubVehicleResponseDto>> running,
            Map<Long, List<HubVehicleResponseDto>> returning, Map<Long, List<HubVehicleResponseDto>> maintenance) {
        HubFleetResponseDto at(Long hubId) {
            return new HubFleetResponseDto(group(idle, hubId), group(running, hubId), group(returning, hubId), group(maintenance, hubId));
        }

        private static HubVehicleGroupResponseDto group(Map<Long, List<HubVehicleResponseDto>> groups, Long hubId) {
            var rows = groups.getOrDefault(hubId, List.of());
            return new HubVehicleGroupResponseDto(rows.size(), rows);
        }
    }
}
