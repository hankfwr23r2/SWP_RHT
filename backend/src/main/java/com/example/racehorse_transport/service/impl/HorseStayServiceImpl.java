package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.health.HorseStayResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.CostCategory;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import com.example.racehorse_transport.dto.operation.HubStablingRequestDto;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.security.access.AccessDeniedException;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.TransferMethod;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.service.HorseStayService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HorseStayServiceImpl implements HorseStayService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    // Chưa có chặng sau thì dự kiến tập kết tối đa một đêm (docs/NGHIEP_VU_THEO_59_BANG.md mục 1.2)
    private static final Duration DEFAULT_ASSEMBLY = Duration.ofHours(24);

    private final HorseStayRepository stays;
    private final HorseLegRepository legs;
    private final BookingHorseRepository bookingHorses;
    private final CostItemRepository costItems;
    private final StaffRepository staff;
    private final Clock clock;

    @Override
    @Transactional
    public void openAssembly(HorseLeg alightedLeg, Instant at) {
        Instant plannedEnd = legs.findByBookingHorse_IdOrderBySequenceNumber(alightedLeg.getBookingHorse().getId()).stream()
                .filter(l -> l.getSequenceNumber() > alightedLeg.getSequenceNumber() && l.getStatus() == HorseLegStatus.PLANNED)
                .map(HorseLeg::getPlannedStartAt).filter(start -> start.isAfter(at))
                .findFirst().orElse(at.plus(DEFAULT_ASSEMBLY));
        HorseStay stay = new HorseStay();
        stay.setBookingHorse(alightedLeg.getBookingHorse());
        stay.setStayType(HorseStayType.ASSEMBLY);
        stay.setLocation(alightedLeg.getAlightTripStop().getLocation());
        stay.setPlannedStartAt(at);
        stay.setPlannedEndAt(plannedEnd);
        stay.setActualStartAt(at);
        stays.save(stay);
    }

    @Override
    @Transactional
    public void closeActive(Long bookingHorseId, Instant at) {
        stays.findFirstByBookingHorse_IdAndActualStartAtIsNotNullAndActualEndAtIsNull(bookingHorseId).ifPresent(stay -> stay.setActualEndAt(at));
    }

    @Override
    @Transactional
    public HorseStayResponseDto planSelfDropOff(Long bookingHorseId, Instant plannedStartAt) {
        BookingHorse line = bookingHorses.findById(bookingHorseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa trong đơn " + bookingHorseId));
        Booking booking = line.getBooking();
        if (booking.getPickupMethod() != TransferMethod.SELF || booking.getDeparture() == null) {
            throw new DispatchRuleException("bookingHorseId", "Chỉ hẹn nhận tại hub cho đơn đi trục mà khách tự chở ngựa tới hub.");
        }
        if (line.getStatus() != BookingHorseStatus.ACTIVE || booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new DispatchRuleException("bookingHorseId", "Đơn " + booking.getCode() + " không ở trạng thái hẹn nhận ngựa được.", true);
        }
        Location hub = booking.getDeparture().getRoute().getOrigin();
        Instant departAt = booking.getDeparture().getPlannedDepartureAt();
        if (hub.getLocationType() != LocationType.HUB) {
            throw new DispatchRuleException("bookingHorseId", "Tuyến của đơn không bắt đầu ở hub.");
        }
        if (!plannedStartAt.isBefore(departAt)) {
            throw new DispatchRuleException("plannedStartAt", "Giờ hẹn phải trước giờ xuất phát của chuyến.");
        }
        if (stays.findByBookingHorse_IdInOrderById(List.of(line.getId())).stream().anyMatch(s -> s.getActualEndAt() == null)) {
            throw new DispatchRuleException("bookingHorseId", "Con ngựa đã có lịch hẹn hoặc đang lưu trú.", true);
        }
        HorseStay stay = new HorseStay();
        stay.setBookingHorse(line);
        stay.setStayType(HorseStayType.ASSEMBLY);
        stay.setLocation(hub);
        stay.setPlannedStartAt(plannedStartAt);
        stay.setPlannedEndAt(departAt);
        stays.saveAndFlush(stay);
        return HorseStayResponseDto.from(stay);
    }

    @Override
    @Transactional
    public HorseStayResponseDto setCollectionTime(Long horseStayId, Instant collectAt) {
        HorseStay stay = selfCollectionStay(horseStayId);
        if (!collectAt.isAfter(stay.getActualStartAt())) {
            throw new DispatchRuleException("collectAt", "Giờ hẹn phải sau lúc ngựa tới hub.");
        }
        stay.setPlannedEndAt(collectAt);
        stays.flush();
        return HorseStayResponseDto.from(stay);
    }

    @Override
    @Transactional
    public HorseStayResponseDto stableOverdue(Long horseStayId, HubStablingRequestDto request, String username) {
        HorseStay assembly = selfCollectionStay(horseStayId);
        Instant now = clock.instant();
        if (!now.isAfter(assembly.getPlannedEndAt())) {
            throw new DispatchRuleException("horseStayId", "Chưa quá giờ hẹn người nhận tới (" + TIME.format(assembly.getPlannedEndAt()) + ").", true);
        }
        Staff actor = staff.findByUser_Username(username).orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên."));
        assembly.setActualEndAt(now);
        HorseStay stabling = new HorseStay();
        stabling.setBookingHorse(assembly.getBookingHorse());
        stabling.setStayType(HorseStayType.STABLING);
        stabling.setLocation(assembly.getLocation());
        stabling.setPlannedStartAt(now);
        stabling.setPlannedEndAt(now.plus(DEFAULT_ASSEMBLY));
        stabling.setActualStartAt(now);
        stays.saveAndFlush(stabling);
        // Phí gửi chuồng người nhận chịu (NGHIEP_VU GĐ8 mục 6); quản lý duyệt rồi mới vào quyết toán
        CostItem fee = new CostItem();
        fee.setCategory(CostCategory.STABLING);
        fee.setDescription("Gửi chuồng tại " + assembly.getLocation().getName() + " do người nhận tới trễ hơn giờ hẹn"
                + (request.note() == null || request.note().isBlank() ? "" : ". " + request.note().strip()));
        fee.setAmount(request.fee());
        fee.setCurrencyCode("VND");
        fee.setPayerType(CostPayerType.CONSIGNEE);
        fee.setBooking(assembly.getBookingHorse().getBooking());
        fee.setHorseStay(stabling);
        fee.setIncurredAt(now);
        fee.setRecordedBy(actor);
        costItems.saveAndFlush(fee);
        return HorseStayResponseDto.from(stabling);
    }

    // Lưu trú tập kết đang diễn ra của con mà người nhận tự tới hub nhận, ngựa đã tới bằng chặng cuối
    private HorseStay selfCollectionStay(Long id) {
        HorseStay stay = stays.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lưu trú " + id));
        Booking booking = stay.getBookingHorse().getBooking();
        boolean arrived = legs.findByBookingHorse_IdOrderBySequenceNumber(stay.getBookingHorse().getId()).stream()
                .noneMatch(l -> l.getStatus() == HorseLegStatus.PLANNED || l.getStatus() == HorseLegStatus.IN_PROGRESS);
        if (stay.getStayType() != HorseStayType.ASSEMBLY || stay.getActualStartAt() == null || stay.getActualEndAt() != null
                || booking.getDeliveryMethod() != TransferMethod.SELF || booking.getStatus() != BookingStatus.IN_OPERATION || !arrived) {
            throw new DispatchRuleException("horseStayId", "Con ngựa này không chờ người nhận tới hub.", true);
        }
        return stay;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HorseStayResponseDto> appointmentsAt(Long locationId) {
        // Đơn đã rời chuyến hoặc bị hủy thì lịch hẹn không còn hiệu lực
        return stays.findByLocation_IdAndActualStartAtIsNullOrderByPlannedStartAt(locationId).stream()
                .filter(stay -> stay.getBookingHorse().getBooking().getStatus() == BookingStatus.CONFIRMED
                        && stay.getBookingHorse().getStatus() == BookingHorseStatus.ACTIVE)
                .map(HorseStayResponseDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HorseStayResponseDto> activeAt(Long locationId) {
        return stays.findByLocation_IdAndActualStartAtIsNotNullAndActualEndAtIsNullOrderByActualStartAt(locationId).stream()
                .map(HorseStayResponseDto::from).toList();
    }
}
