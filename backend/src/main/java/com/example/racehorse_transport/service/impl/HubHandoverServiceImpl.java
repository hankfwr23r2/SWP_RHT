package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.dto.health.HorseStayResponseDto;
import org.springframework.web.multipart.MultipartFile;
import com.example.racehorse_transport.dto.operation.HubHandoverRequestDto;
import com.example.racehorse_transport.dto.operation.HubRefusalRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HandoverHorse;
import com.example.racehorse_transport.entity.HandoverHorseId;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.HorseInsuranceStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.entity.enums.TransferMethod;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.TripExecutionPolicy;
import com.example.racehorse_transport.repository.HandoverHorseRepository;
import com.example.racehorse_transport.repository.HandoverRepository;
import com.example.racehorse_transport.repository.HorseInsuranceRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.DocumentCompletenessService;
import com.example.racehorse_transport.service.HubHandoverService;
import com.example.racehorse_transport.service.HandoverOutcomeService;
import com.example.racehorse_transport.service.HandoverEvidenceService;
import java.time.Clock;
import java.time.Instant;
import com.example.racehorse_transport.dto.operation.HandoverPaperResponseDto;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HubHandoverServiceImpl implements HubHandoverService {

    private final HorseStayRepository stays;
    private final HorseLegRepository legs;
    private final HandoverRepository handovers;
    private final HandoverHorseRepository scans;
    private final StaffRepository staff;
    private final HorseInsuranceRepository insurances;
    private final DocumentCompletenessService documents;
    private final HandoverOutcomeService outcomes;
    private final Clock clock;
    private final HandoverEvidenceService evidence;
    private final BookingRepository bookings;

    @Override
    @Transactional(readOnly = true)
    public List<HandoverPaperResponseDto> papers(Long horseStayId, boolean receiving) {
        HorseStay stay = stays.findById(horseStayId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lưu trú " + horseStayId));
        Booking booking = stay.getBookingHorse().getBooking();
        return evidence.originals(null, Set.of(booking.getId()), receiving).stream()
                .map(document -> HandoverPaperResponseDto.from(document, booking.getCode(), null)).toList();
    }

    @Override
    @Transactional
    public HorseStayResponseDto receive(Long horseStayId, HubHandoverRequestDto request, MultipartFile photo, MultipartFile signedRecord, String username) {
        Long bookingId = stays.findBookingId(horseStayId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lưu trú " + horseStayId));
        bookings.findForUpdate(bookingId).orElseThrow();
        HorseStay stay = awaitingSelfDropOff(horseStayId);
        Booking booking = stay.getBookingHorse().getBooking();
        outcomes.requireSelfDeliveryBeforeCheckIn(stay.getBookingHorse().getId());
        // Đầu SELF không có chặng đón: ngựa được ghi vào chặng trục sẽ lên xe ở chính hub này
        HorseLeg next = chain(stay).stream().findFirst()
                .filter(l -> l.getStatus() == HorseLegStatus.PLANNED && l.getBoardTripStop() != null
                        && Objects.equals(l.getBoardTripStop().getLocation() == null ? null : l.getBoardTripStop().getLocation().getId(), stay.getLocation().getId()))
                .orElseThrow(() -> new DispatchRuleException("horseStayId", "Con ngựa chưa được xếp lượt xe trục đi từ hub này. Xếp xe trước khi nhận.", true));
        boolean insured = insurances.findByBookingHorse_Id(stay.getBookingHorse().getId())
                .map(i -> i.getStatus() == HorseInsuranceStatus.CONFIRMED).orElse(false);
        if (!insured) {
            throw new DispatchRuleException("insurance", "Bảo hiểm của " + stay.getBookingHorse().getHorse().getName() + " chưa được xác nhận.", true);
        }
        if (!documents.isComplete(booking.getId())) {
            throw new DispatchRuleException("documents", "Đơn " + booking.getCode() + " còn thiếu giấy tờ bắt buộc. Không nhận ngựa tại hub.", true);
        }
        requireChip(stay, request);
        Instant now = clock.instant();
        Handover handover = record(stay, next, HandoverType.PICKUP, request, photo, signedRecord, username, now);
        stay.setActualStartAt(now);
        stays.flush();
        // Lập khoản số dư (xe chỉ rời hub khi đã trả đủ, #32) và báo khách
        outcomes.atHub(handover, next, handover.getHandledBy().getUserId());
        return HorseStayResponseDto.from(stay);
    }

    @Override
    @Transactional
    public void refuse(Long horseStayId, HubRefusalRequestDto request, String username) {
        Long bookingId = stays.findBookingId(horseStayId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lưu trú " + horseStayId));
        bookings.findForUpdate(bookingId).orElseThrow();
        HorseStay stay = awaitingSelfDropOff(horseStayId);
        outcomes.refuse(stay.getBookingHorse().getId(), request.reason().strip(), clock.instant(), actor(username).getUserId());
    }

    @Override
    @Transactional
    public HorseStayResponseDto deliver(Long horseStayId, HubHandoverRequestDto request, MultipartFile photo, MultipartFile signedRecord, String username) {
        Long bookingId = stays.findBookingId(horseStayId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lưu trú " + horseStayId));
        bookings.findForUpdate(bookingId).orElseThrow();
        // Người nhận tới trễ: ngựa đang gửi chuồng tại hub vẫn trả được
        HorseStay stay = stays.findForUpdate(horseStayId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lưu trú " + horseStayId));
        if (stay.getStayType() != HorseStayType.ASSEMBLY && stay.getStayType() != HorseStayType.STABLING) {
            throw new DispatchRuleException("horseStayId", "Chỉ giao nhận với khách ở lưu trú tập kết hoặc gửi chuồng tại hub.", true);
        }
        Booking booking = stay.getBookingHorse().getBooking();
        boolean unpaidAssembly = stay.getStayType() == HorseStayType.ASSEMBLY && booking.getStatus() == BookingStatus.CONFIRMED
                && bookings.hasReceivedHorses(bookingId) && !outcomes.canBoardAtHub(bookingId);
        if ((booking.getDeliveryMethod() != TransferMethod.SELF && stay.getStayType() != HorseStayType.STABLING && !unpaidAssembly)
                || stay.getActualStartAt() == null || stay.getActualEndAt() != null) {
            throw new DispatchRuleException("horseStayId", "Con ngựa này không chờ người nhận tới hub.", true);
        }
        if (stay.getBalanceHoldAt() != null || unpaidAssembly) {
            requireChip(stay, request);
            var leg = legs.findByBookingHorse_IdOrderBySequenceNumber(stay.getBookingHorse().getId()).stream()
                    .reduce((first, next) -> next)
                    .orElseThrow(() -> new DispatchRuleException("horseStayId", "Không tìm thấy chặng gắn với lần nhận ngựa.", true));
            Instant now = clock.instant();
            if (unpaidAssembly) outcomes.prepareUnpaidHubReturn(stay, now);
            Handover handover = record(stay, leg, HandoverType.RETURN, request, photo, signedRecord, username, now);
            outcomes.atHub(handover, leg, handover.getHandledBy().getUserId());
            return HorseStayResponseDto.from(stay);
        }
        if (booking.getStatus() != BookingStatus.IN_OPERATION) {
            throw new DispatchRuleException("horseStayId", "Đơn " + booking.getCode() + " không ở trạng thái giao ngựa được.", true);
        }
        // Ngựa đã tới hub này bằng chặng cuối, không còn chặng nào phía sau
        List<HorseLeg> journey = legs.findByBookingHorse_IdOrderBySequenceNumber(stay.getBookingHorse().getId()).stream()
                .filter(leg -> leg.getStatus() != HorseLegStatus.CANCELLED).toList();
        HorseLeg last = journey.stream().filter(leg -> leg.getStatus() != HorseLegStatus.PLANNED)
                .reduce((first, next) -> next).orElse(null);
        boolean selfCollection = booking.getDeliveryMethod() == TransferMethod.SELF && last != null
                && last == journey.getLast() && last.getStatus() == HorseLegStatus.COMPLETED && last.getAlightTripStop() != null
                && sameLocation(stay, last.getAlightTripStop().getLocation());
        boolean collectionAfterFailure = stay.getStayType() == HorseStayType.STABLING && last != null
                && last.getStatus() == HorseLegStatus.FAILED && last.getVehicleTrip() != null
                && sameLocation(stay, last.getVehicleTrip().getEndLocation());
        if (!selfCollection && !collectionAfterFailure) {
            throw new DispatchRuleException("horseStayId", "Con ngựa chưa tới hub này bằng chặng cuối.", true);
        }
        requireChip(stay, request);
        Instant now = clock.instant();
        Handover handover = record(stay, last, HandoverType.DELIVERY, request, photo, signedRecord, username, now);
        if (collectionAfterFailure) {
            journey.stream().filter(leg -> leg.getStatus() == HorseLegStatus.PLANNED && leg.getReplacedHorseLeg() != null)
                    .forEach(leg -> {
                        leg.setStatus(HorseLegStatus.CANCELLED);
                        leg.setStallPosition(null);
                    });
        }
        // Con cuối của đơn giao xong thì đơn DELIVERED (#16); báo khách
        outcomes.atHub(handover, last, handover.getHandledBy().getUserId());
        return HorseStayResponseDto.from(stay);
    }

    // Lịch hẹn khách tự chở tới còn chờ nhận: con còn trong đơn, đơn đã xác nhận
    private HorseStay awaitingSelfDropOff(Long horseStayId) {
        HorseStay stay = assemblyStay(horseStayId);
        Booking booking = stay.getBookingHorse().getBooking();
        if ((booking.getPickupMethod() != TransferMethod.SELF && stay.getBookingHorse().getPickupMissChoice()
                != com.example.racehorse_transport.entity.enums.PickupMissChoice.SELF_DELIVERY) || stay.getActualStartAt() != null) {
            throw new DispatchRuleException("horseStayId", "Lịch hẹn này không chờ nhận ngựa khách tự chở tới.", true);
        }
        if (stay.getBookingHorse().getStatus() != BookingHorseStatus.ACTIVE
                || (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.IN_OPERATION)) {
            throw new DispatchRuleException("horseStayId", "Đơn " + booking.getCode() + " không ở trạng thái nhận ngựa được.", true);
        }
        return stay;
    }

    private HorseStay assemblyStay(Long id) {
        HorseStay stay = stays.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lưu trú " + id));
        if (stay.getStayType() != HorseStayType.ASSEMBLY) {
            throw new DispatchRuleException("horseStayId", "Chỉ giao nhận với khách ở lưu trú tập kết tại hub.", true);
        }
        return stay;
    }

    private List<HorseLeg> chain(HorseStay stay) {
        return legs.findByBookingHorse_IdOrderBySequenceNumber(stay.getBookingHorse().getId()).stream()
                .filter(l -> l.getStatus() != HorseLegStatus.CANCELLED && l.getStatus() != HorseLegStatus.FAILED).toList();
    }

    private static boolean sameLocation(HorseStay stay, Location location) {
        return location != null && location.getId().equals(stay.getLocation().getId());
    }

    private static void requireChip(HorseStay stay, HubHandoverRequestDto request) {
        if (!TripExecutionPolicy.chipMatches(request.microchip(), stay.getBookingHorse().getHorse().getMicrochip())) {
            throw new DispatchRuleException("microchip", "Chip không khớp hồ sơ ngựa. Không giao nhận.", true);
        }
    }

    // handover gắn horse_stay (không có điểm dừng); handover_horse ghi chặng liền kề của con ngựa
    private Handover record(HorseStay stay, HorseLeg leg, HandoverType type, HubHandoverRequestDto request, MultipartFile photo, MultipartFile signedRecord, String username, Instant at) {
        Handover handover = new Handover();
        handover.setHandoverType(type);
        handover.setHorseStay(stay);
        handover.setHandledBy(actor(username));
        handover.setStatus(HandoverStatus.DONE);
        handover.setHandedAt(at);
        handovers.saveAndFlush(handover);
        HandoverHorse row = new HandoverHorse();
        row.setId(new HandoverHorseId(handover.getId(), leg.getId()));
        row.setHandover(handover);
        row.setHorseLeg(leg);
        row.setScannedMicrochip(request.microchip().strip());
        row.setMicrochipMatched(true);
        row.setConditionNote(request.conditionNote() == null || request.conditionNote().isBlank() ? null : request.conditionNote().strip());
        if (type == HandoverType.DELIVERY) {
            row.setRecipientName(evidence.requireRecipient(leg.getBookingHorse().getBooking(), request.recipient()));
        }
        scans.saveAndFlush(row);
        evidence.recordHubPapers(handover, leg.getBookingHorse().getBooking().getId(), type == HandoverType.PICKUP, request.papers());
        evidence.attachPhoto(row, photo, handover.getHandledBy().getUserId());
        evidence.attachRecord(handover.getId(), signedRecord, handover.getHandledBy().getUserId());
        return handover;
    }

    private Staff actor(String username) {
        return staff.findByUser_Username(username).orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên."));
    }
}
