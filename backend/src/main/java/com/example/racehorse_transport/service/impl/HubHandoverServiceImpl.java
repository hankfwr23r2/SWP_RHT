package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.health.HorseStayResponseDto;
import com.example.racehorse_transport.dto.operation.HubHandoverRequestDto;
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
import com.example.racehorse_transport.service.BookingStatusService;
import com.example.racehorse_transport.service.DocumentCompletenessService;
import com.example.racehorse_transport.service.HubHandoverService;
import com.example.racehorse_transport.service.PaymentService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
    private final PaymentService payments;
    private final BookingStatusService bookingStatus;
    private final Clock clock;

    @Override
    @Transactional
    public HorseStayResponseDto receive(Long horseStayId, HubHandoverRequestDto request, String username) {
        HorseStay stay = assemblyStay(horseStayId);
        Booking booking = stay.getBookingHorse().getBooking();
        if (booking.getPickupMethod() != TransferMethod.SELF || stay.getActualStartAt() != null) {
            throw new DispatchRuleException("horseStayId", "Lịch hẹn này không chờ nhận ngựa khách tự chở tới.", true);
        }
        if (stay.getBookingHorse().getStatus() != BookingHorseStatus.ACTIVE || booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new DispatchRuleException("horseStayId", "Đơn " + booking.getCode() + " không ở trạng thái nhận ngựa được.", true);
        }
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
        record(stay, next, HandoverType.PICKUP, request, username, now);
        stay.setActualStartAt(now);
        stays.flush();
        // Nhận ngựa từ khách xong thì lập khoản số dư; xe chỉ rời hub khi đã trả đủ (#32)
        payments.requestBalance(booking.getId(), now);
        return HorseStayResponseDto.from(stay);
    }

    @Override
    @Transactional
    public HorseStayResponseDto deliver(Long horseStayId, HubHandoverRequestDto request, String username) {
        HorseStay stay = assemblyStay(horseStayId);
        Booking booking = stay.getBookingHorse().getBooking();
        if (booking.getDeliveryMethod() != TransferMethod.SELF || stay.getActualStartAt() == null || stay.getActualEndAt() != null) {
            throw new DispatchRuleException("horseStayId", "Con ngựa này không chờ người nhận tới hub.", true);
        }
        if (booking.getStatus() != BookingStatus.IN_OPERATION) {
            throw new DispatchRuleException("horseStayId", "Đơn " + booking.getCode() + " không ở trạng thái giao ngựa được.", true);
        }
        // Ngựa đã tới hub này bằng chặng cuối, không còn chặng nào phía sau
        List<HorseLeg> chain = chain(stay);
        HorseLeg last = chain.isEmpty() ? null : chain.getLast();
        if (last == null || last.getStatus() != HorseLegStatus.COMPLETED || last.getAlightTripStop() == null
                || last.getAlightTripStop().getLocation() == null || !last.getAlightTripStop().getLocation().getId().equals(stay.getLocation().getId())) {
            throw new DispatchRuleException("horseStayId", "Con ngựa chưa tới hub này bằng chặng cuối.", true);
        }
        requireChip(stay, request);
        Instant now = clock.instant();
        record(stay, last, HandoverType.DELIVERY, request, username, now);
        stay.setActualEndAt(now);
        stays.flush();
        if (legs.countActiveHorsesNotDelivered(booking.getId()) == 0) {
            bookingStatus.transition(booking.getId(), BookingStatus.DELIVERED, "Người nhận đã nhận đủ ngựa tại hub", actor(username).getUserId());
        }
        return HorseStayResponseDto.from(stay);
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

    private static void requireChip(HorseStay stay, HubHandoverRequestDto request) {
        if (!TripExecutionPolicy.chipMatches(request.microchip(), stay.getBookingHorse().getHorse().getMicrochip())) {
            throw new DispatchRuleException("microchip", "Chip không khớp hồ sơ ngựa. Không giao nhận.", true);
        }
    }

    // handover gắn horse_stay (không có điểm dừng); handover_horse ghi chặng liền kề của con ngựa
    private void record(HorseStay stay, HorseLeg leg, HandoverType type, HubHandoverRequestDto request, String username, Instant at) {
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
        scans.saveAndFlush(row);
    }

    private Staff actor(String username) {
        return staff.findByUser_Username(username).orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên."));
    }
}
