package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.CrewRole;
import com.example.racehorse_transport.entity.enums.HorseInsuranceStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.TripExecutionPolicy;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.HorseInsuranceRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.BookingStatusService;
import com.example.racehorse_transport.service.DocumentCompletenessService;
import com.example.racehorse_transport.service.StopReadinessService;
import com.example.racehorse_transport.service.TripExecutionService;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripExecutionServiceImpl implements TripExecutionService {

    private static final Set<HorseLegStatus> NOT_STARTED = Set.of(HorseLegStatus.PLANNED);

    private final VehicleTripRepository trips;
    private final TripStopRepository stops;
    private final CrewAssignmentRepository crew;
    private final HorseLegRepository legs;
    private final HorseInsuranceRepository insurances;
    private final DocumentCompletenessService documents;
    private final StopReadinessService readiness;
    private final BookingStatusService bookingStatus;
    private final Clock clock;

    @Override
    @Transactional
    public void start(Long vehicleTripId, String username) {
        VehicleTrip trip = trips.findForUpdate(vehicleTripId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + vehicleTripId));
        requireCrew(trip.getId(), username);
        TripExecutionPolicy.requireStartable(trip.getStatus(),
                crew.existsByVehicleTrip_IdAndCrewRoleAndStatus(trip.getId(), CrewRole.DRIVER, CrewAssignmentStatus.ACKNOWLEDGED));
        // Quy tắc #40: sơ đồ khoang chốt trước khi lượt xe bắt đầu, mọi con (kể cả con đón ở điểm sau) phải có vị trí
        List<String> unplaced = legs.findActiveOnTrip(trip.getId(), Set.of(HorseLegStatus.CANCELLED, HorseLegStatus.FAILED)).stream()
                .filter(leg -> leg.getStallPosition() == null).map(leg -> leg.getBookingHorse().getHorse().getName()).toList();
        if (!unplaced.isEmpty()) {
            throw new DispatchRuleException("stallPlan", "Còn ngựa chưa có vị trí khoang: " + String.join(", ", unplaced)
                    + ". Xếp sơ đồ khoang trước khi xuất phát.", true);
        }
        // Đơn có ngựa lên xe ở địa chỉ đón trên lượt này: đủ giấy và bảo hiểm từng con đã xác nhận
        Map<Long, String> bookings = new LinkedHashMap<>();
        for (TripStop stop : stops.findByVehicleTrip_IdOrderBySequenceNumber(trip.getId())) {
            if (stop.getStopType() != TripStopType.PICKUP) continue;
            for (HorseLeg leg : legs.findByBoardTripStop_IdAndStatusIn(stop.getId(), NOT_STARTED)) {
                var booking = leg.getBookingHorse().getBooking();
                bookings.put(booking.getId(), booking.getCode());
                boolean insured = insurances.findByBookingHorse_Id(leg.getBookingHorse().getId())
                        .map(i -> i.getStatus() == HorseInsuranceStatus.CONFIRMED).orElse(false);
                if (!insured) {
                    throw new DispatchRuleException("insurance", "Bảo hiểm của " + leg.getBookingHorse().getHorse().getName()
                            + " (đơn " + booking.getCode() + ") chưa được xác nhận.", true);
                }
            }
        }
        bookings.forEach((id, code) -> {
            if (!documents.isComplete(id)) {
                throw new DispatchRuleException("documents", "Đơn " + code + " còn thiếu giấy tờ bắt buộc.", true);
            }
        });
        trip.setStatus(VehicleTripStatus.IN_PROGRESS);
        trip.setActualStartAt(clock.instant());
    }

    @Override
    @Transactional
    public Long arrive(Long tripStopId, String username) {
        TripStop stop = findStop(tripStopId);
        VehicleTrip trip = trips.findForUpdate(stop.getVehicleTrip().getId()).orElseThrow();
        requireCrew(trip.getId(), username);
        TripExecutionPolicy.requireInProgress(trip.getStatus());
        TripStop previous = previousOf(stop);
        TripExecutionPolicy.requireCanArrive(stop.getActualArrivalAt() != null, previous == null || previous.getActualDepartureAt() != null);
        stop.setActualArrivalAt(clock.instant());
        return trip.getId();
    }

    @Override
    @Transactional
    public Long depart(Long tripStopId, String earlyReason, String username) {
        TripStop stop = findStop(tripStopId);
        VehicleTrip trip = trips.findForUpdate(stop.getVehicleTrip().getId()).orElseThrow();
        CrewAssignment member = requireCrew(trip.getId(), username);
        TripExecutionPolicy.requireInProgress(trip.getStatus());
        readiness.requireCanLeave(stop, earlyReason, member.getStaff().getUserId());
        if (stop.getStopType() == TripStopType.PICKUP || stop.getStopType() == TripStopType.BORDER || stop.getStopType() == TripStopType.HUB) {
            leavePickup(stop, member.getStaff().getUserId());
        }
        stop.setActualDepartureAt(clock.instant());
        return trip.getId();
    }

    @Override
    @Transactional
    public void complete(Long vehicleTripId, String username) {
        VehicleTrip trip = trips.findForUpdate(vehicleTripId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + vehicleTripId));
        requireCrew(trip.getId(), username);
        TripExecutionPolicy.requireInProgress(trip.getStatus());
        List<TripStop> all = stops.findByVehicleTrip_IdOrderBySequenceNumber(trip.getId());
        TripExecutionPolicy.requireCanComplete(all.getLast().getActualArrivalAt() != null,
                legs.countByVehicleTrip_IdAndStatus(trip.getId(), HorseLegStatus.IN_PROGRESS));
        trip.setStatus(VehicleTripStatus.COMPLETED);
        trip.setActualEndAt(clock.instant());
    }

    // Đã qua kiểm thanh toán (#32): đơn có ngựa đã nhận lên xe ở điểm đón này sang IN_OPERATION
    private void leavePickup(TripStop stop, Long actorUserId) {
        legs.findByBoardTripStop_IdAndStatusIn(stop.getId(), Set.of(HorseLegStatus.IN_PROGRESS)).stream()
                .map(leg -> leg.getBookingHorse().getBooking())
                .filter(booking -> booking.getStatus() == BookingStatus.CONFIRMED)
                .map(booking -> booking.getId()).distinct()
                .forEach(id -> bookingStatus.transition(id, BookingStatus.IN_OPERATION, "Đã nhận ngựa và xe rời nơi nhận", actorUserId));
    }

    private CrewAssignment requireCrew(Long tripId, String username) {
        return crew.findFirstByVehicleTrip_IdAndStaff_User_UsernameAndStatusNot(tripId, username, CrewAssignmentStatus.RELEASED)
                .orElseThrow(() -> new AccessDeniedException("Bạn không ở trong tổ lái của lượt xe này."));
    }

    private TripStop findStop(Long id) {
        return stops.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm dừng " + id));
    }

    private TripStop previousOf(TripStop stop) {
        return stops.findByVehicleTrip_IdOrderBySequenceNumber(stop.getVehicleTrip().getId()).stream()
                .filter(s -> s.getSequenceNumber() < stop.getSequenceNumber())
                .reduce((first, second) -> second).orElse(null);
    }
}
