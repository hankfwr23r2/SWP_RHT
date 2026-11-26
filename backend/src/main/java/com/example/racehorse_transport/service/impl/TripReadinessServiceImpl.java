package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.CrewRole;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.IncidentSeverity;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.IncidentPolicy;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.HandoverReadinessService;
import com.example.racehorse_transport.service.PaymentReadinessService;
import com.example.racehorse_transport.service.TripReadinessService;
import com.example.racehorse_transport.service.VehicleService;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripReadinessServiceImpl implements TripReadinessService {
    private static final Set<CrewRole> DRIVING_CREW = Set.of(CrewRole.DRIVER, CrewRole.ESCORT);

    private final TripStopRepository stops;
    private final HorseLegRepository legs;
    private final HandoverReadinessService handoverReadiness;
    private final PaymentReadinessService paymentReadiness;
    private final VehicleService vehicles;
    private final IncidentRepository incidents;
    private final VehicleTripRepository trips;
    private final CrewAssignmentRepository crew;
    private final StaffRepository staff;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireVehicleAndCrewAvailable(VehicleTrip trip) {
        // Các lượt xuất phát đồng thời khóa cùng xe và khóa nhân viên theo thứ tự ID.
        var vehicle = vehicles.lockVehicle(trip.getVehicle().getId());
        trips.findFirstByVehicle_IdAndStatusAndIdNotOrderById(vehicle.getId(), VehicleTripStatus.IN_PROGRESS, trip.getId())
                .ifPresent(other -> {
                    throw new DispatchRuleException("vehicleId", "Xe còn lượt " + other.getCode() + " chưa kết thúc.", true);
                });
        if (vehicle.getStatus() != VehicleStatus.ACTIVE) {
            throw new DispatchRuleException("vehicleId", "Xe đang bảo dưỡng hoặc đã ngừng sử dụng. Liên hệ điều phối để đổi xe.", true);
        }
        var staffIds = crew.findAssignedStaffIds(trip.getId(), CrewAssignmentStatus.RELEASED, DRIVING_CREW);
        for (Long id : staffIds) {
            staff.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên " + id));
        }
        if (!staffIds.isEmpty()) {
            var otherTrips = crew.findRunningTripCodes(staffIds, trip.getId(), CrewAssignmentStatus.RELEASED,
                    DRIVING_CREW, VehicleTripStatus.IN_PROGRESS);
            if (!otherTrips.isEmpty()) {
                throw new DispatchRuleException("staffId", "Tổ lái còn lượt " + otherTrips.getFirst() + " chưa kết thúc.", true);
            }
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireReadyToStart(VehicleTrip trip) {
        Map<Long, Booking> bookings = new TreeMap<>();
        for (TripStop stop : stops.findByVehicleTrip_IdOrderBySequenceNumber(trip.getId())) {
            if (stop.getStopType() != TripStopType.PICKUP) continue;
            for (HorseLeg leg : legs.findByBoardTripStop_IdAndStatusIn(stop.getId(), Set.of(HorseLegStatus.PLANNED))) {
                var horse = leg.getBookingHorse();
                if (horse.getPickupMissedAt() != null && horse.getPickupMissChoice() == null) {
                    throw new DispatchRuleException("pickup", "Còn ngựa chưa chọn cách xử lý đón hụt.", true);
                }
                var booking = horse.getBooking();
                bookings.put(booking.getId(), booking);
                handoverReadiness.requireConfirmedInsurance(horse);
            }
        }
        bookings.forEach((id, booking) -> {
            paymentReadiness.lockBooking(id);
            handoverReadiness.requireDocumentsForPickup(booking);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public void requireTripUnblocked(Long tripId) {
        if (incidents.findForTripBySeverity(tripId, IncidentSeverity.HIGH).stream()
                .anyMatch(incident -> IncidentPolicy.blocksTrip(incident.getSeverity(), incident.getStatus(), incident.getTripUnlockedAt()))) {
            throw new DispatchRuleException("tripStopId",
                    "Lượt xe đang có sự cố nặng chưa có phương án được duyệt. Chờ quản lý duyệt hoặc điều phối mở khóa.", true);
        }
    }
}
