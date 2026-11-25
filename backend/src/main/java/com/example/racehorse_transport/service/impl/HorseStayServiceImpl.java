package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.service.StablingChargeService;
import com.example.racehorse_transport.dto.health.HorseStayResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.PartyRoleType;
import com.example.racehorse_transport.dto.health.StablePartnerResponseDto;
import com.example.racehorse_transport.policy.DispatchPolicy;
import com.example.racehorse_transport.repository.PartyRepository;
import com.example.racehorse_transport.repository.PartyRoleRepository;
import java.time.LocalDate;
import com.example.racehorse_transport.dto.operation.HubStablingRequestDto;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import com.example.racehorse_transport.service.CustomerNoticeService;
import com.example.racehorse_transport.service.HorseStayService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HorseStayServiceImpl implements HorseStayService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    // Chưa có chặng sau thì dự kiến tập kết tối đa một đêm (docs/NGHIEP_VU_THEO_59_BANG.md mục 1.2)
    private static final Duration DEFAULT_ASSEMBLY = Duration.ofHours(24);
    // Nghỉ đêm 10 giờ (OVERNIGHT_REST_HOURS), dự phòng thêm khi điểm dừng không có giờ rời dự kiến
    private static final Duration OVERNIGHT = Duration.ofHours(12);
    private static final String MAX_DAYS = "STABLING_MAX_DAYS";
    private static final String DAILY_FEE = "STABLING_DAILY_FEE";

    private final HorseStayRepository stays;
    private final HorseLegRepository legs;
    private final BookingHorseRepository bookingHorses;
    private final StablingChargeService stablingCharges;
    private final Clock clock;
    private final PartyRepository parties;
    private final PartyRoleRepository partyRoles;
    private final CustomerNoticeService notices;
    private final SystemConfigService configs;
    private final NotificationService notifications;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void openBalanceHold(Long bookingId, Instant chargedFrom, Departure missedDeparture) {
        Instant until = chargedFrom.plus(Duration.ofDays(configs.getInt(MAX_DAYS)));
        stays.findByBookingHorse_Booking_Id(bookingId).stream()
                .filter(stay -> stay.getStayType() == HorseStayType.ASSEMBLY && stay.getLocation().getLocationType() == LocationType.HUB)
                .filter(stay -> stay.getActualStartAt() != null && stay.getActualEndAt() == null)
                .forEach(stay -> {
                    stay.setStayType(HorseStayType.STABLING);
                    stay.setBalanceHoldAt(chargedFrom);
                    stay.setMissedDeparture(missedDeparture);
                    stay.setStablingUnitPrice(java.math.BigDecimal.valueOf(configs.getInt(DAILY_FEE)));
                    if (until.isAfter(stay.getPlannedStartAt())) stay.setPlannedEndAt(until);
                });
    }

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
    public void openOvernight(HorseLeg leg, TripStop stop, Party operator, Instant at) {
        HorseStay stay = new HorseStay();
        stay.setBookingHorse(leg.getBookingHorse());
        stay.setStayType(HorseStayType.OVERNIGHT);
        stay.setLocation(stop.getLocation());
        stay.setOperatorParty(operator);
        stay.setPlannedStartAt(at);
        stay.setPlannedEndAt(stop.getPlannedDepartureAt() != null && stop.getPlannedDepartureAt().isAfter(at) ? stop.getPlannedDepartureAt() : at.plus(OVERNIGHT));
        stay.setActualStartAt(at);
        stays.save(stay);
    }

    @Override
    @Transactional(readOnly = true)
    public Party requireStablePartner(Long partyId) {
        Party party = parties.findById(partyId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đối tác " + partyId));
        if (!isStable(party.getId())) {
            throw new DispatchRuleException("partyId", party.getDisplayName() + " không có vai trò chuồng gửi còn hiệu lực.");
        }
        return party;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StablePartnerResponseDto> stablePartners() {
        return parties.findAll().stream().filter(p -> isStable(p.getId()))
                .sorted(Comparator.comparing(Party::getDisplayName)).map(StablePartnerResponseDto::from).toList();
    }

    private boolean isStable(Long partyId) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        return partyRoles.findById_PartyId(partyId).stream()
                .anyMatch(r -> r.getId().getRoleType() == PartyRoleType.STABLE && DispatchPolicy.roleValidOn(r.getValidFrom(), r.getValidTo(), today));
    }

    @Override
    @Transactional
    public void requireStablingPaid(Long bookingHorseId) {
        stays.findFirstByBookingHorse_IdAndActualStartAtIsNotNullAndActualEndAtIsNull(bookingHorseId)
                .ifPresent(stablingCharges::requirePaid);
    }

    @Override
    @Transactional
    public void closeActive(Long bookingHorseId, Instant at, Long actorUserId) {
        stays.findFirstByBookingHorse_IdAndActualStartAtIsNotNullAndActualEndAtIsNull(bookingHorseId).ifPresent(stay -> {
            stay.setActualEndAt(at);
            stablingCharges.chargeThrough(stay, at, actorUserId);
        });
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void openFailedDeliveryStabling(HorseLeg failedLeg, Location hub, Instant at, Long actorUserId) {
        HorseStay stay = new HorseStay();
        stay.setBookingHorse(failedLeg.getBookingHorse());
        stay.setFailedDeliveryLeg(failedLeg);
        stay.setStayType(HorseStayType.STABLING);
        stay.setStablingUnitPrice(java.math.BigDecimal.valueOf(configs.getInt(DAILY_FEE)));
        stay.setLocation(hub);
        stay.setPlannedStartAt(at);
        stay.setPlannedEndAt(at.plus(Duration.ofDays(configs.getInt(MAX_DAYS))));
        stay.setActualStartAt(at);
        stays.save(stay);
        Booking booking = failedLeg.getBookingHorse().getBooking();
        notifications.notify(List.of(booking.getCustomer().getUser().getId()), booking.getId(), "Ngựa đang được giữ tại hub",
                failedLeg.getBookingHorse().getHorse().getName() + " chưa giao được do không gặp người nhận, đang được giữ và chăm sóc tại "
                        + hub.getName() + " tới " + TIME.format(stay.getPlannedEndAt()) + ". Phí lưu chuồng và chăm sóc "
                        + money(configs.getInt(DAILY_FEE)) + " cho mỗi 24 giờ trọn vẹn, người nhận chịu; chưa đủ 24 giờ không tính.", "/orders/" + booking.getId());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void openReturnAssembly(HorseLeg failedLeg, Location hub, Instant at) {
        HorseStay stay = new HorseStay();
        stay.setBookingHorse(failedLeg.getBookingHorse());
        stay.setStayType(HorseStayType.ASSEMBLY);
        stay.setLocation(hub);
        stay.setPlannedStartAt(at);
        stay.setPlannedEndAt(at.plus(DEFAULT_ASSEMBLY));
        stay.setActualStartAt(at);
        stays.save(stay);
    }

    @Override
    @Transactional
    public int remindOverdueStabling() {
        return stablingCharges.remindOverdueStabling();
    }

    private static String money(long amount) {
        return String.format(java.util.Locale.ROOT, "%,d", amount).replace(',', '.') + " ₫";
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void dropAppointments(Long bookingHorseId) {
        stays.deleteByBookingHorse_IdAndActualStartAtIsNull(bookingHorseId);
    }

    @Override
    @Transactional
    public HorseStayResponseDto planSelfDropOff(Long bookingHorseId, Instant plannedStartAt) {
        BookingHorse line = bookingHorses.findById(bookingHorseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa trong đơn " + bookingHorseId));
        Booking booking = line.getBooking();
        if ((booking.getPickupMethod() != TransferMethod.SELF
                && line.getPickupMissChoice() != com.example.racehorse_transport.entity.enums.PickupMissChoice.SELF_DELIVERY)
                || booking.getDeparture() == null) {
            throw new DispatchRuleException("bookingHorseId", "Chỉ hẹn nhận tại hub cho đơn đi trục mà khách tự chở ngựa tới hub.");
        }
        if (line.getStatus() != BookingHorseStatus.ACTIVE || (booking.getStatus() != BookingStatus.CONFIRMED
                && booking.getStatus() != BookingStatus.IN_OPERATION)) {
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
        notices.dropOffAppointed(stay);
        return HorseStayResponseDto.from(stay);
    }

    @Override
    @Transactional
    public HorseStayResponseDto setCollectionTime(Long horseStayId, Instant collectAt) {
        HorseStay stay = selfCollectionStay(horseStayId);
        if (!collectAt.isAfter(stay.getActualStartAt())) {
            throw new DispatchRuleException("collectAt", "Giờ hẹn phải sau lúc ngựa tới hub.");
        }
        boolean changed = !collectAt.equals(stay.getPlannedEndAt());
        stay.setPlannedEndAt(collectAt);
        stays.flush();
        if (changed) {
            notices.collectionAppointed(stay);
        }
        return HorseStayResponseDto.from(stay);
    }

    @Override
    @Transactional
    public HorseStayResponseDto stableOverdue(Long horseStayId, HubStablingRequestDto request) {
        HorseStay assembly = selfCollectionStay(horseStayId);
        Instant now = clock.instant();
        if (!now.isAfter(assembly.getPlannedEndAt())) {
            throw new DispatchRuleException("horseStayId", "Chưa quá giờ hẹn người nhận tới (" + TIME.format(assembly.getPlannedEndAt()) + ").", true);
        }
        assembly.setActualEndAt(now);
        HorseStay stabling = new HorseStay();
        stabling.setBookingHorse(assembly.getBookingHorse());
        stabling.setStayType(HorseStayType.STABLING);
        stabling.setLocation(assembly.getLocation());
        // Người nhận trễ hẹn: phí tính từ giờ hẹn
        stabling.setPlannedStartAt(assembly.getPlannedEndAt());
        stabling.setStablingUnitPrice(java.math.BigDecimal.valueOf(configs.getInt(DAILY_FEE)));
        stabling.setPlannedEndAt(now.plus(Duration.ofDays(configs.getInt(MAX_DAYS))));
        stabling.setActualStartAt(now);
        stays.saveAndFlush(stabling);
        Booking booking = assembly.getBookingHorse().getBooking();
        String note = request.note() == null || request.note().isBlank() ? "" : " " + request.note().strip();
        notifications.notify(List.of(booking.getCustomer().getUser().getId()), booking.getId(), "Ngựa đang được giữ tại hub",
                "Đã quá giờ hẹn. Phí lưu chuồng và chăm sóc tại " + assembly.getLocation().getName()
                        + " là " + money(configs.getInt(DAILY_FEE)) + " cho mỗi 24 giờ trọn vẹn tính từ giờ hẹn." + note, "/orders/" + booking.getId());
        return HorseStayResponseDto.from(stabling);
    }

    // Lưu trú tập kết đang diễn ra của con mà người nhận tự tới hub nhận, ngựa đã tới bằng chặng cuối
    private HorseStay selfCollectionStay(Long id) {
        HorseStay stay = stays.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lưu trú " + id));
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
                .filter(stay -> {
                    var line = stay.getBookingHorse();
                    var status = line.getBooking().getStatus();
                    return line.getStatus() == BookingHorseStatus.ACTIVE && (status == BookingStatus.CONFIRMED
                            || (status == BookingStatus.IN_OPERATION && line.getPickupMissChoice()
                                == com.example.racehorse_transport.entity.enums.PickupMissChoice.SELF_DELIVERY));
                })
                .map(HorseStayResponseDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HorseStayResponseDto> activeAt(Long locationId) {
        var unpaid = java.util.Set.copyOf(stays.findUnpaidAssemblyStayIds(locationId));
        return stays.findByLocation_IdAndActualStartAtIsNotNullAndActualEndAtIsNullOrderByActualStartAt(locationId).stream()
                .map(stay -> HorseStayResponseDto.from(stay, stay.getBalanceHoldAt() != null || unpaid.contains(stay.getId())))
                .toList();
    }
}
