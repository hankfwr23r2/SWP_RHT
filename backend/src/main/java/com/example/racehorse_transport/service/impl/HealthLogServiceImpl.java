package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.health.HealthLogRequestDto;
import com.example.racehorse_transport.dto.health.HealthLogResponseDto;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.HorseHealthLog;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.HealthLogPolicy;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.HorseHealthLogRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.service.HealthLogService;
import com.example.racehorse_transport.service.NotificationService;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HealthLogServiceImpl implements HealthLogService {

    private final HorseHealthLogRepository healthLogs;
    private final BookingHorseRepository bookingHorses;
    private final TripStopRepository stops;
    private final HorseStayRepository stays;
    private final HorseLegRepository legs;
    private final CrewAssignmentRepository crew;
    private final StaffRepository staff;
    private final NotificationService notifications;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<HealthLogResponseDto> ofHorse(Long bookingHorseId) {
        return healthLogs.findByBookingHorse_IdOrderByRecordedAtDesc(bookingHorseId).stream().map(HealthLogResponseDto::from).toList();
    }

    @Override
    @Transactional
    public HealthLogResponseDto record(HealthLogRequestDto request, String username) {
        HealthLogPolicy.requireOnePlace(request.tripStopId() != null, request.horseStayId() != null);
        BookingHorse line = bookingHorses.findById(request.bookingHorseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa trong đơn " + request.bookingHorseId()));
        Staff recorder = staff.findByUser_Username(username).orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên."));
        TripStop stop = request.tripStopId() == null ? null : stopWhereHorseIsOnBoard(request.tripStopId(), line, username);
        HorseStay stay = request.horseStayId() == null ? null : activeStayOf(request.horseStayId(), line);

        HorseHealthLog log = request.toEntity(line, stop, stay);
        healthLogs.findFirstByBookingHorse_IdOrderByRecordedAtDesc(line.getId())
                .filter(previous -> HealthLogPolicy.refusedWaterTwice(request.waterIntake(), previous.getWaterIntake()))
                .ifPresent(previous -> {
                    log.setHealthStatus(HealthLogPolicy.atLeastWatch(request.healthStatus()));
                    notifyCoordinators(line);
                });
        log.setRecordedBy(recorder);
        log.setRecordedAt(clock.instant());
        return HealthLogResponseDto.from(healthLogs.saveAndFlush(log));
    }

    // Người ghi trong tổ lái; xe đang ở điểm này; con ngựa đang trên xe ở điểm này
    private TripStop stopWhereHorseIsOnBoard(Long tripStopId, BookingHorse line, String username) {
        TripStop stop = stops.findById(tripStopId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm dừng " + tripStopId));
        var trip = stop.getVehicleTrip();
        crew.findFirstByVehicleTrip_IdAndStaff_User_UsernameAndStatusNot(trip.getId(), username, CrewAssignmentStatus.RELEASED)
                .orElseThrow(() -> new AccessDeniedException("Bạn không ở trong tổ lái của lượt xe này."));
        if (trip.getStatus() != VehicleTripStatus.IN_PROGRESS || stop.getActualArrivalAt() == null || stop.getActualDepartureAt() != null) {
            throw new DispatchRuleException("tripStopId", "Chỉ ghi khi xe đang ở điểm dừng này.", true);
        }
        int sequence = stop.getSequenceNumber();
        boolean onBoard = legs.findByBookingHorse_IdOrderBySequenceNumber(line.getId()).stream()
                .anyMatch(l -> l.getVehicleTrip() != null && l.getVehicleTrip().getId().equals(trip.getId())
                        && Set.of(HorseLegStatus.IN_PROGRESS, HorseLegStatus.COMPLETED).contains(l.getStatus())
                        && l.getBoardTripStop().getSequenceNumber() <= sequence && sequence <= l.getAlightTripStop().getSequenceNumber());
        if (!onBoard) {
            throw new DispatchRuleException("bookingHorseId", "Con ngựa này không ở trên xe tại điểm dừng này.", true);
        }
        return stop;
    }

    private HorseStay activeStayOf(Long horseStayId, BookingHorse line) {
        HorseStay stay = stays.findById(horseStayId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lần lưu trú " + horseStayId));
        if (!Objects.equals(stay.getBookingHorse().getId(), line.getId()) || stay.getActualStartAt() == null || stay.getActualEndAt() != null) {
            throw new DispatchRuleException("horseStayId", "Lần lưu trú này không phải của con ngựa hoặc đã kết thúc.", true);
        }
        return stay;
    }

    private void notifyCoordinators(BookingHorse line) {
        List<Long> coordinators = staff.findAllByOrderByStaffCodeAsc().stream()
                .filter(s -> "COORDINATOR".equals(s.getUser().getRole().getCode()) && s.getEmploymentStatus() == EmploymentStatus.ACTIVE
                        && s.getUser().getStatus() == AppUserStatus.ACTIVE)
                .map(Staff::getUserId).toList();
        if (coordinators.isEmpty()) return;
        notifications.notify(coordinators, line.getBooking().getId(), "Ngựa bỏ uống hai lần dừng liền",
                line.getHorse().getName() + " (đơn " + line.getBooking().getCode() + ") không uống nước ở hai lần dừng liền, đã chuyển sang theo dõi.", null);
    }
}
