package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.operation.CrewTripResponseDto;
import com.example.racehorse_transport.dto.operation.HandoverCreateRequestDto;
import com.example.racehorse_transport.dto.operation.HandoverResponseDto;
import com.example.racehorse_transport.dto.operation.HandoverScanRequestDto;
import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HandoverHorse;
import com.example.racehorse_transport.entity.HandoverHorseId;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.TripExecutionPolicy;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.HandoverHorseRepository;
import com.example.racehorse_transport.repository.HandoverRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.service.BookingStatusService;
import com.example.racehorse_transport.service.DispatchService;
import com.example.racehorse_transport.service.HandoverService;
import com.example.racehorse_transport.service.HorseLegService;
import com.example.racehorse_transport.service.HorseStayService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HandoverServiceImpl implements HandoverService {

    private final HandoverRepository handovers;
    private final HandoverHorseRepository scans;
    private final HorseLegRepository legs;
    private final TripStopRepository stops;
    private final CrewAssignmentRepository crew;
    private final BookingStatusService bookingStatus;
    private final DispatchService dispatch;
    private final HorseLegService horseLegs;
    private final HorseStayService horseStays;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public CrewTripResponseDto crewTrip(Long vehicleTripId, String username) {
        requireCrew(vehicleTripId, username);
        List<Long> stopIds = stops.findByVehicleTrip_IdOrderBySequenceNumber(vehicleTripId).stream().map(TripStop::getId).toList();
        List<HandoverResponseDto> done = handovers.findByTripStop_IdInOrderById(stopIds).stream().map(this::describe).toList();
        return new CrewTripResponseDto(dispatch.trip(vehicleTripId), horseLegs.horsesOnTrip(vehicleTripId), done);
    }

    @Override
    @Transactional
    public CrewTripResponseDto create(Long tripStopId, HandoverCreateRequestDto request, String username) {
        TripStop stop = stops.findById(tripStopId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm dừng " + tripStopId));
        Long tripId = stop.getVehicleTrip().getId();
        CrewAssignment member = requireCrew(tripId, username);
        TripExecutionPolicy.requireInProgress(stop.getVehicleTrip().getStatus());
        if (stop.getActualArrivalAt() == null || stop.getActualDepartureAt() != null) {
            throw new DispatchRuleException("tripStopId", "Chỉ giao nhận khi xe đang ở điểm dừng này.", true);
        }
        TripExecutionPolicy.requireHandoverFitsStop(request.handoverType(), stop.getStopType());
        Handover handover = request.toEntity(stop, member.getStaff());
        if (candidates(handover).isEmpty()) {
            throw new DispatchRuleException("handoverType", "Không có con ngựa nào cần giao nhận loại này ở điểm dừng này.", true);
        }
        handover.setStatus(HandoverStatus.PENDING);
        handovers.saveAndFlush(handover);
        return crewTrip(tripId, username);
    }

    @Override
    @Transactional
    public CrewTripResponseDto scan(Long handoverId, HandoverScanRequestDto request, String username) {
        Handover handover = pending(handoverId, username);
        HorseLeg leg = candidates(handover).stream().filter(l -> l.getId().equals(request.horseLegId())).findFirst()
                .orElseThrow(() -> new DispatchRuleException("horseLegId", "Con ngựa này không thuộc lần giao nhận.", true));
        HandoverHorse scan = scans.findById(new HandoverHorseId(handover.getId(), leg.getId())).orElseGet(() -> {
            HandoverHorse created = new HandoverHorse();
            created.setId(new HandoverHorseId(handover.getId(), leg.getId()));
            created.setHandover(handover);
            created.setHorseLeg(leg);
            return created;
        });
        scan.setScannedMicrochip(request.microchip().strip());
        scan.setMicrochipMatched(TripExecutionPolicy.chipMatches(request.microchip(), leg.getBookingHorse().getHorse().getMicrochip()));
        scan.setConditionNote(request.conditionNote() == null || request.conditionNote().isBlank() ? null : request.conditionNote().strip());
        scans.saveAndFlush(scan);
        return crewTrip(handover.getTripStop().getVehicleTrip().getId(), username);
    }

    @Override
    @Transactional
    public CrewTripResponseDto complete(Long handoverId, String username) {
        Handover handover = pending(handoverId, username);
        Long tripId = handover.getTripStop().getVehicleTrip().getId();
        Long actorId = requireCrew(tripId, username).getStaff().getUserId();
        List<HandoverHorse> results = scans.findById_HandoverId(handover.getId());
        TripExecutionPolicy.requireSomeMatched(results.stream().map(HandoverHorse::getMicrochipMatched).toList());
        var now = clock.instant();
        boolean boarding = TripExecutionPolicy.isBoarding(handover.getHandoverType());
        for (HandoverHorse result : results) {
            if (!Boolean.TRUE.equals(result.getMicrochipMatched())) continue;
            HorseLeg leg = result.getHorseLeg();
            if (boarding) {
                leg.setStatus(HorseLegStatus.IN_PROGRESS);
                leg.setActualStartAt(now);
                // Nhận từ hub: kết thúc lưu trú tập kết
                horseStays.closeActive(leg.getBookingHorse().getId(), now);
            } else {
                leg.setStatus(HorseLegStatus.COMPLETED);
                leg.setActualEndAt(now);
                if (handover.getHandoverType() == HandoverType.HUB_IN) {
                    horseStays.openAssembly(leg, now);
                }
            }
        }
        handover.setStatus(HandoverStatus.DONE);
        handover.setHandedAt(now);
        legs.flush();
        if (!boarding) {
            // Quy tắc #16: con ACTIVE cuối cùng của đơn giao xong thì đơn DELIVERED
            results.stream().filter(r -> Boolean.TRUE.equals(r.getMicrochipMatched()))
                    .map(r -> r.getHorseLeg().getBookingHorse().getBooking()).distinct()
                    .filter(booking -> booking.getStatus() == BookingStatus.IN_OPERATION && legs.countActiveHorsesNotDelivered(booking.getId()) == 0)
                    .forEach(booking -> bookingStatus.transition(booking.getId(), BookingStatus.DELIVERED, "Đã giao xong mọi con ngựa", actorId));
        }
        return crewTrip(tripId, username);
    }

    // Con cần giao nhận: lên xe ở điểm này (đón, ra hub) hoặc xuống xe ở điểm này (giao, vào hub)
    private List<HorseLeg> candidates(Handover handover) {
        Long stopId = handover.getTripStop().getId();
        return TripExecutionPolicy.isBoarding(handover.getHandoverType())
                ? legs.findByBoardTripStop_IdAndStatusIn(stopId, Set.of(HorseLegStatus.PLANNED, HorseLegStatus.IN_PROGRESS))
                : legs.findByAlightTripStop_IdAndStatusIn(stopId, Set.of(HorseLegStatus.IN_PROGRESS, HorseLegStatus.COMPLETED));
    }

    private HandoverResponseDto describe(Handover handover) {
        Map<Long, HandoverHorse> byLeg = scans.findById_HandoverId(handover.getId()).stream()
                .collect(Collectors.toMap(s -> s.getId().getHorseLegId(), s -> s));
        return HandoverResponseDto.from(handover, candidates(handover), byLeg);
    }

    private Handover pending(Long handoverId, String username) {
        Handover handover = handovers.findForUpdate(handoverId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lần giao nhận " + handoverId));
        requireCrew(handover.getTripStop().getVehicleTrip().getId(), username);
        if (handover.getStatus() != HandoverStatus.PENDING) {
            throw new DispatchRuleException("status", "Lần giao nhận đã kết thúc.", true);
        }
        if (handover.getTripStop().getVehicleTrip().getStatus() != VehicleTripStatus.IN_PROGRESS) {
            throw new DispatchRuleException("status", "Lượt xe chưa xuất phát hoặc đã kết thúc.", true);
        }
        return handover;
    }

    private CrewAssignment requireCrew(Long tripId, String username) {
        return crew.findFirstByVehicleTrip_IdAndStaff_User_UsernameAndStatusNot(tripId, username, CrewAssignmentStatus.RELEASED)
                .orElseThrow(() -> new AccessDeniedException("Bạn không ở trong tổ lái của lượt xe này."));
    }
}
