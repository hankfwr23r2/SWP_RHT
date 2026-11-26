package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.health.HealthLogRequestDto;
import com.example.racehorse_transport.dto.health.HealthLogResponseDto;
import com.example.racehorse_transport.dto.health.HorseStayResponseDto;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HorseHealthLog;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.CrewRole;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.entity.enums.HealthStatus;
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
import com.example.racehorse_transport.service.CoordinatorRecipientService;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.service.HealthLogService;
import com.example.racehorse_transport.service.HealthCheckTaskService;
import com.example.racehorse_transport.service.NotificationService;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
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
    private final CoordinatorRecipientService coordinators;
    private final NotificationService notifications;
    private final Clock clock;
    private final HealthCheckTaskService healthTasks;

    @Override
    @Transactional(readOnly = true)
    public List<HorseStayResponseDto> myStays(String username) {
        Staff recorder = recorder(username);
        if (!isCrew(roleOf(recorder))) throw new AccessDeniedException("Chỉ tổ xe được xem danh sách này.");
        var active = stays.findByActualStartAtIsNotNullAndActualEndAtIsNullOrderByActualStartAt().stream()
                .filter(stay -> HealthLogPolicy.canRecordStay(stay.getLocation().getLocationType(), stay.getStayType(), stay.getOperatorParty() != null))
                .toList();
        var handlers = healthTasks.latestCompanyHandovers(active.stream().map(stay -> stay.getBookingHorse().getId()).distinct().toList());
        return active.stream()
                .filter(stay -> signedBy(handlers.get(stay.getBookingHorse().getId()), recorder))
                .map(HorseStayResponseDto::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HealthLogResponseDto> ofHorse(Long bookingHorseId) {
        return healthLogs.findByBookingHorse_IdOrderByRecordedAtDesc(bookingHorseId).stream().map(HealthLogResponseDto::from).toList();
    }

    @Override
    @Transactional
    public HealthLogResponseDto record(HealthLogRequestDto request, String username) {
        Staff recorder = recorder(username);
        String role = roleOf(recorder);
        boolean coordinator = "COORDINATOR".equals(role);
        if ((!coordinator && !isCrew(role)) || (coordinator && request.tripStopId() != null)) {
            throw new AccessDeniedException("Bạn không có quyền ghi sức khỏe tại đây.");
        }
        HealthLogPolicy.requireOnePlace(request.tripStopId() != null, request.horseStayId() != null);
        BookingHorse line = bookingHorses.findById(request.bookingHorseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa trong đơn " + request.bookingHorseId()));
        TripStop stop = request.tripStopId() == null ? null : stopWhereHorseIsOnBoard(request.tripStopId(), line, username);
        HorseStay stay = request.horseStayId() == null ? null : activeStayOf(request.horseStayId(), line);

        Instant recordedAt = clock.instant();
        if (stay != null && !coordinator
                && !("ESCORT".equals(role) && healthTasks.mayRecordStay(recorder.getUserId(), stay, recordedAt))
                && !signedBy(healthTasks.latestCompanyHandovers(List.of(line.getId())).get(line.getId()), recorder)) {
            throw new AccessDeniedException("Bạn không phụ trách con ngựa này.");
        }

        HorseHealthLog log = request.toEntity(line, stop, stay);
        if (coordinator) {
            log.setNote(HealthLogPolicy.reportedNote(request.reportedBy(), request.reportChannel(), request.note()));
        }
        healthLogs.findFirstByBookingHorse_IdOrderByRecordedAtDesc(line.getId())
                .filter(previous -> HealthLogPolicy.refusedWaterTwice(request.waterIntake(), previous.getWaterIntake()))
                .ifPresent(previous -> {
                    log.setHealthStatus(HealthLogPolicy.atLeastWatch(request.healthStatus()));
                    notifyCoordinators(line, stop == null ? null : stop.getVehicleTrip());
                });
        HorseHealthLog saved = save(log, recorder, recordedAt);
        if (saved.getHealthStatus() == HealthStatus.SICK || saved.getHealthStatus() == HealthStatus.CRITICAL) {
            notifyCoordinatorsForHealth(line, saved.getHealthStatus(), stop == null ? null : stop.getVehicleTrip());
        }
        return HealthLogResponseDto.from(saved);
    }

    @Override
    @Transactional
    public void recordVetResult(VehicleTrip trip, BookingHorse affectedHorse, String note, String username) {
        Staff recorder = recorder(username);
        Long tripId = trip.getId();
        if (!"COORDINATOR".equals(roleOf(recorder)) && (!"ESCORT".equals(roleOf(recorder))
                || crew.findFirstByVehicleTrip_IdAndStaff_User_UsernameAndStatusNot(tripId, username, CrewAssignmentStatus.RELEASED)
                        .filter(assignment -> assignment.getCrewRole() == CrewRole.ESCORT).isEmpty())) {
            throw new AccessDeniedException("Chỉ điều phối hoặc hộ tống của lượt xe được ghi kết quả thú y.");
        }
        TripStop stop = stops.findFirstByVehicleTrip_IdAndActualArrivalAtIsNotNullOrderBySequenceNumberDesc(tripId)
                .orElseThrow(() -> new DispatchRuleException("tripStopId", "Lượt xe chưa tới điểm dừng nào để ghi kết quả thú y.", true));
        List<BookingHorse> horses = affectedHorse == null
                ? legs.findOnBoardForIncident(tripId, Set.of(HorseLegStatus.IN_PROGRESS, HorseLegStatus.FAILED)).stream()
                        .map(HorseLeg::getBookingHorse).distinct().toList()
                : List.of(affectedHorse);
        if (horses.isEmpty()) {
            throw new DispatchRuleException("incidentId", "Không có con ngựa nào đang trên xe để ghi kết quả thú y.", true);
        }
        Instant now = clock.instant();
        for (BookingHorse horse : horses) {
            // Ghi chú thú y cần theo dõi, không làm giảm mức độ nặng đã ghi.
            HealthStatus status = healthLogs.findFirstByBookingHorse_IdOrderByRecordedAtDesc(horse.getId())
                    .map(HorseHealthLog::getHealthStatus).map(HealthLogPolicy::atLeastWatch).orElse(HealthStatus.WATCH);
            var entry = new HealthLogRequestDto(horse.getId(), stop.getId(), null, status,
                    null, null, null, null, null, null, null, note, null, null);
            save(entry.toEntity(horse, stop, null), recorder, now);
        }
    }

    private HorseHealthLog save(HorseHealthLog log, Staff recorder, Instant now) {
        log.setRecordedBy(recorder);
        log.setRecordedAt(now);
        HorseHealthLog saved = healthLogs.saveAndFlush(log);
        healthTasks.completeFrom(saved);
        return saved;
    }

    private Staff recorder(String username) {
        return coordinators.staffByUsername(username);
    }

    private static String roleOf(Staff recorder) {
        return recorder.getUser().getRole().getCode();
    }

    private static boolean isCrew(String role) {
        return "ESCORT".equals(role) || "DRIVER".equals(role);
    }

    private static boolean signedBy(Handover handover, Staff recorder) {
        return handover != null && Objects.equals(handover.getHandledBy().getUserId(), recorder.getUserId());
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
        HorseStay stay = stays.findForUpdate(horseStayId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lần lưu trú " + horseStayId));
        if (!Objects.equals(stay.getBookingHorse().getId(), line.getId()) || stay.getActualStartAt() == null || stay.getActualEndAt() != null) {
            throw new DispatchRuleException("horseStayId", "Lần lưu trú này không phải của con ngựa hoặc đã kết thúc.", true);
        }
        if (!HealthLogPolicy.canRecordStay(stay.getLocation().getLocationType(), stay.getStayType(), stay.getOperatorParty() != null)) {
            throw new AccessDeniedException("Chỉ ghi sức khỏe trong lần lưu trú ở hub hoặc chuồng.");
        }
        return stay;
    }

    private void notifyCoordinators(BookingHorse line, VehicleTrip trip) {
        List<Long> recipients = coordinators.forHorse(line, trip);
        if (recipients.isEmpty()) return;
        notifications.notify(recipients, line.getBooking().getId(), "Ngựa bỏ uống hai lần dừng liền",
                line.getHorse().getName() + " (đơn " + line.getBooking().getCode() + ") không uống nước ở hai lần dừng liền, đã chuyển sang theo dõi.", null);
    }

    private void notifyCoordinatorsForHealth(BookingHorse line, HealthStatus status, VehicleTrip trip) {
        List<Long> recipients = coordinators.forHorse(line, trip);
        if (recipients.isEmpty()) return;
        String label = status == HealthStatus.CRITICAL ? "Nguy cấp" : "Ốm";
        notifications.notify(recipients, line.getBooking().getId(),
                "Sức khỏe ngựa " + label,
                line.getHorse().getName() + " (đơn " + line.getBooking().getCode() + ") được ghi " + label + ".",
                "/coordinator/incidents");
    }
}
