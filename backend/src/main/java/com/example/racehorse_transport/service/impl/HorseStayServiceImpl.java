package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.health.HorseStayResponseDto;
import com.example.racehorse_transport.entity.Booking;
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

    // Chưa có chặng sau thì dự kiến tập kết tối đa một đêm (docs/NGHIEP_VU_THEO_59_BANG.md mục 1.2)
    private static final Duration DEFAULT_ASSEMBLY = Duration.ofHours(24);

    private final HorseStayRepository stays;
    private final HorseLegRepository legs;
    private final BookingHorseRepository bookingHorses;

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
