package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.dispatch.CrewAssignmentResponseDto;
import com.example.racehorse_transport.dto.dispatch.TripStopResponseDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripCreateRequestDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripResponseDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripUpdateRequestDto;
import com.example.racehorse_transport.dto.dispatch.DispatchVehicleResponseDto;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.DispatchPolicy;
import com.example.racehorse_transport.policy.DispatchPolicy.Interval;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.DispatchService;
import com.example.racehorse_transport.service.CoordinatorAssignmentService;
import com.example.racehorse_transport.service.VehicleService;
import com.example.racehorse_transport.service.TripPlanningService;
import com.example.racehorse_transport.service.TripPlanEditService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DispatchServiceImpl implements DispatchService {

    private final VehicleTripRepository trips;
    private final TripStopRepository stops;
    private final CrewAssignmentRepository crew;
    private final HorseLegRepository legs;
    private final VehicleService vehicles;
    private final CoordinatorAssignmentService assignments;
    private final TripPlanningService planning;
    private final TripPlanEditService edits;

    @Override
    @Transactional(readOnly = true)
    public List<VehicleTripResponseDto> trips(Instant from, Instant to) {
        if (!to.isAfter(from)) {
            throw new DispatchRuleException("to", "Khoảng thời gian không hợp lệ.");
        }
        return describe(trips.findOverlapping(from, to));
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleTripResponseDto trip(Long id) {
        return describe(List.of(findTrip(id))).getFirst();
    }

    @Override
    @Transactional
    public VehicleTripResponseDto createTrip(VehicleTripCreateRequestDto request, String username) {
        return trip(planning.createTrip(request, username, true));
    }

    @Override
    @Transactional
    public VehicleTripResponseDto createCalculatedTrip(VehicleTripCreateRequestDto request, String username) {
        return trip(planning.createTrip(request, username, false));
    }

    @Override
    @Transactional
    public VehicleTripResponseDto updateTrip(Long id, VehicleTripUpdateRequestDto request, String username) {
        edits.updateTrip(id, request, username);
        return trip(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DispatchVehicleResponseDto> vehicles() {
        return vehicles.vehiclesForDispatch();
    }

    @Override
    @Transactional
    public VehicleTripResponseDto swapVehicle(Long tripId, Long vehicleId, Long actorUserId, String reason, Instant changedAt) {
        VehicleTrip trip = trips.findForUpdate(tripId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + tripId));
        if (trip.getStatus() != VehicleTripStatus.IN_PROGRESS) {
            throw new DispatchRuleException("tripId", "Chỉ đổi xe khi lượt xe đang chạy.", true);
        }
        Long currentId = trip.getVehicle().getId();
        if (vehicleId.equals(currentId)) {
            throw new DispatchRuleException("vehicleId", "Chọn xe khác xe đang chạy.");
        }
        // Khóa theo ID để hai yêu cầu đổi xe không giữ khóa theo thứ tự ngược nhau.
        Vehicle first = vehicles.lockVehicle(Math.min(currentId, vehicleId));
        Vehicle second = vehicles.lockVehicle(Math.max(currentId, vehicleId));
        Vehicle current = currentId.equals(first.getId()) ? first : second;
        Vehicle vehicle = vehicleId.equals(first.getId()) ? first : second;
        DispatchPolicy.requireActiveVehicle(vehicle.getStatus());
        // Xe cứu hộ tới từ nơi khác nên không áp quy tắc bắt đầu ở nơi lượt trước kết thúc; chỉ không trùng giờ phần còn lại của lượt
        Interval rest = new Interval(changedAt, trip.getPlannedEndAt().isAfter(changedAt) ? trip.getPlannedEndAt() : changedAt.plusSeconds(60));
        DispatchPolicy.requireNoOverlap("vehicleId", "Xe", rest, trips.findByVehicle_IdAndStatusNot(vehicle.getId(), VehicleTripStatus.CANCELLED).stream()
                .filter(t -> !t.getId().equals(trip.getId())).map(t -> new Interval(t.getPlannedStartAt(), t.getPlannedEndAt())).toList());
        int capacity = vehicle.getStandardStallCount() * StallSeatPolicy.seats("STANDARD") + vehicle.getWideStallCount() * StallSeatPolicy.seats("WIDE");
        int used = legs.findActiveOnTrip(trip.getId(), Set.of(HorseLegStatus.CANCELLED, HorseLegStatus.FAILED, HorseLegStatus.COMPLETED)).stream()
                .filter(l -> l.getStallPosition() != null)
                .mapToInt(l -> l.getStallPosition() + StallSeatPolicy.seats(l.getBookingHorse().getStallType().getCode()) - 1).max().orElse(0);
        if (used > capacity) {
            throw new DispatchRuleException("vehicleId", "Xe " + vehicle.getLicensePlate() + " chỉ có " + capacity + " chỗ, sơ đồ khoang đang dùng tới chỗ " + used + ".", true);
        }
        trip.setVehicle(vehicle);
        vehicles.markForMaintenance(current, actorUserId, reason, changedAt);
        trips.flush();
        return trip(trip.getId());
    }

    private List<VehicleTripResponseDto> describe(List<VehicleTrip> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        List<Long> ids = found.stream().map(VehicleTrip::getId).toList();
        Map<Long, List<TripStopResponseDto>> stopsByTrip = stops.findByVehicleTrip_IdInOrderByVehicleTrip_IdAscSequenceNumberAsc(ids).stream()
                .collect(Collectors.groupingBy(s -> s.getVehicleTrip().getId(), Collectors.mapping(TripStopResponseDto::from, Collectors.toList())));
        Map<Long, List<CrewAssignmentResponseDto>> crewByTrip = crew.findByVehicleTrip_IdInOrderById(ids).stream()
                .collect(Collectors.groupingBy(a -> a.getVehicleTrip().getId(), Collectors.mapping(CrewAssignmentResponseDto::from, Collectors.toList())));
        Set<Long> manualTripIds = planning.manualTripIds(found.stream()
                .filter(trip -> trip.getStatus() == VehicleTripStatus.PLANNED && trip.getTripType() != VehicleTripType.LINEHAUL)
                .map(VehicleTrip::getId).toList());
        return found.stream().map(trip -> {
            long openIncidents = assignments.openIncidents(trip.getId());
            boolean takeOverAllowed = com.example.racehorse_transport.policy.CoordinatorAssignmentPolicy.canTakeOver(
                    trip.getStatus(), openIncidents > 0);
            return VehicleTripResponseDto.from(trip, stopsByTrip.getOrDefault(trip.getId(), List.of()),
                    crewByTrip.getOrDefault(trip.getId(), List.of()), openIncidents, takeOverAllowed, manualTripIds.contains(trip.getId()));
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleTripResponseDto> ownedTrips(Long staffId) {
        return describe(trips.findByCoordinator_UserIdAndStatusNotInOrderByPlannedStartAtAscIdAsc(staffId,
                com.example.racehorse_transport.policy.CoordinatorAssignmentPolicy.CLOSED_TRIPS));
    }

    private VehicleTrip findTrip(Long id) {
        return trips.findOneById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + id));
    }
}
