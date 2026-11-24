package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.dispatch.DispatchBookingResponseDto;
import com.example.racehorse_transport.dto.dispatch.DispatchBookingResponseDto.HorseDto;
import com.example.racehorse_transport.dto.dispatch.HorseLegRequestDto;
import com.example.racehorse_transport.dto.dispatch.HorseLegResponseDto;
import com.example.racehorse_transport.dto.dispatch.TripHorseResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.HorseLegType;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.DispatchPolicy;
import com.example.racehorse_transport.policy.DispatchPolicy.Occupancy;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.repository.BookingAddressRepository;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.DispatchBookingRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.HorseLegService;
import com.example.racehorse_transport.service.StallPlanService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HorseLegServiceImpl implements HorseLegService {

    private static final Set<BookingStatus> DISPATCHABLE = Set.of(BookingStatus.CONFIRMED, BookingStatus.IN_OPERATION);
    // Chặng không còn tính vào chuỗi và sức chứa
    private static final Set<HorseLegStatus> INACTIVE = Set.of(HorseLegStatus.CANCELLED, HorseLegStatus.FAILED);

    private final HorseLegRepository legs;
    private final VehicleTripRepository trips;
    private final TripStopRepository stops;
    private final BookingHorseRepository bookingHorses;
    private final BookingAddressRepository bookingAddresses;
    private final DispatchBookingRepository dispatchBookings;
    private final StallPlanService stallPlans;

    @Override
    @Transactional(readOnly = true)
    public List<DispatchBookingResponseDto> dispatchableBookings(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new DispatchRuleException("to", "Khoảng ngày không hợp lệ.");
        }
        List<DispatchBookingResponseDto> result = new ArrayList<>();
        for (Booking booking : dispatchBookings.findDispatchable(DISPATCHABLE, from, to)) {
            Map<AddressRole, BookingAddress> addresses = bookingAddresses.findByBooking_Id(booking.getId()).stream()
                    .collect(Collectors.toMap(BookingAddress::getAddressType, a -> a));
            List<BookingHorse> lines = bookingHorses.findForDetail(booking.getId());
            Map<Long, List<HorseLegResponseDto>> legsByHorse = legs.findByBookingHorse_IdInOrderByBookingHorse_IdAscSequenceNumberAsc(
                            lines.stream().map(BookingHorse::getId).toList()).stream()
                    .collect(Collectors.groupingBy(l -> l.getBookingHorse().getId(), Collectors.mapping(HorseLegResponseDto::from, Collectors.toList())));
            List<HorseDto> horses = lines.stream().map(line -> HorseDto.from(line, legsByHorse.getOrDefault(line.getId(), List.of()))).toList();
            result.add(DispatchBookingResponseDto.from(booking, addresses.get(AddressRole.PICKUP), addresses.get(AddressRole.DELIVERY), horses));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripHorseResponseDto> horsesOnTrip(Long vehicleTripId) {
        if (!trips.existsById(vehicleTripId)) {
            throw new ResourceNotFoundException("Không tìm thấy lượt xe " + vehicleTripId);
        }
        return legs.findActiveOnTrip(vehicleTripId, INACTIVE).stream().map(TripHorseResponseDto::from).toList();
    }

    @Override
    @Transactional
    public List<TripHorseResponseDto> assign(Long vehicleTripId, HorseLegRequestDto request) {
        // Khóa lượt xe: hai người gán ngựa cùng lúc không vượt sức chứa
        VehicleTrip trip = trips.findForUpdate(vehicleTripId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + vehicleTripId));
        requireNotStarted(trip);
        BookingHorse line = bookingHorses.findById(request.bookingHorseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa trong đơn " + request.bookingHorseId()));
        Booking booking = line.getBooking();
        if (line.getStatus() != BookingHorseStatus.ACTIVE) {
            throw new DispatchRuleException("bookingHorseId", "Ngựa này đang " + line.getStatus() + ", không xếp xe được.", true);
        }
        if (!DISPATCHABLE.contains(booking.getStatus())) {
            throw new DispatchRuleException("bookingHorseId", "Đơn " + booking.getCode() + " đang " + booking.getStatus() + ", chưa xếp xe được.", true);
        }

        List<TripStop> tripStops = stops.findByVehicleTrip_IdOrderBySequenceNumber(trip.getId());
        TripStop board = stopOf(tripStops, request.boardTripStopId());
        TripStop alight = stopOf(tripStops, request.alightTripStopId());
        DispatchPolicy.requireBoardBeforeAlight(board != null, alight != null,
                board == null ? 0 : board.getSequenceNumber(), alight == null ? 0 : alight.getSequenceNumber());
        requireAddressesOfBooking(booking, board, alight);

        HorseLegType legType = HorseLegType.valueOf(trip.getTripType().name());
        Map<AddressRole, Long> addressIds = bookingAddresses.findByBooking_Id(booking.getId()).stream()
                .collect(Collectors.toMap(BookingAddress::getAddressType, BookingAddress::getId));
        requireLegFitsBooking(legType, trip, booking, line, tripStops, board, alight, addressIds);

        List<HorseLeg> chain = legs.findByBookingHorse_IdOrderBySequenceNumber(line.getId());
        if (chain.stream().anyMatch(l -> !INACTIVE.contains(l.getStatus()) && l.getVehicleTrip() != null && l.getVehicleTrip().getId().equals(trip.getId()))) {
            throw new DispatchRuleException("bookingHorseId", "Ngựa đã có trên lượt xe này.", true);
        }
        HorseLeg previous = chain.stream().filter(l -> !INACTIVE.contains(l.getStatus())).reduce((first, second) -> second).orElse(null);
        HorseLeg last = chain.isEmpty() ? null : chain.getLast();
        HorseLeg failed = last != null && last.getStatus() == HorseLegStatus.FAILED ? last : null;
        if (failed != null) {
            // Giao thất bại: xe đưa ngựa về nơi lượt đó kết thúc (về hub); chặng mới đi tiếp từ đó (B5)
            DispatchPolicy.requireContinuous("L" + failed.getVehicleTrip().getEndLocation().getId(), placeKey(board));
            DispatchPolicy.requireAfterPreviousLeg(failed.getVehicleTrip().getPlannedEndAt(), board.getPlannedDepartureAt());
        } else if (previous != null) {
            DispatchPolicy.requireContinuous(endKey(previous), placeKey(board));
            DispatchPolicy.requireAfterPreviousLeg(previous.getPlannedEndAt(), board.getPlannedDepartureAt());
        } else if (legType != HorseLegType.LINEHAUL) {
            // Chặng đầu tiên đi từ địa chỉ đón; riêng chặng trục có thể là chặng đầu (khách tự đưa ngựa tới hub, hoặc nhận ở cửa khẩu)
            DispatchPolicy.requireContinuous("A" + addressIds.get(AddressRole.PICKUP), placeKey(board));
        }

        List<Occupancy> occupancies = new ArrayList<>(legs.findActiveOnTrip(trip.getId(), INACTIVE).stream()
                .map(l -> new Occupancy(l.getBoardTripStop().getSequenceNumber(), l.getAlightTripStop().getSequenceNumber(),
                        StallSeatPolicy.seats(l.getBookingHorse().getStallType().getCode())))
                .toList());
        occupancies.add(new Occupancy(board.getSequenceNumber(), alight.getSequenceNumber(), StallSeatPolicy.seats(line.getStallType().getCode())));
        DispatchPolicy.requireCapacity(occupancies, trip.getVehicle().getStandardStallCount() * StallSeatPolicy.seats("STANDARD")
                + trip.getVehicle().getWideStallCount() * StallSeatPolicy.seats("WIDE"));
        DispatchPolicy.requirePlannedWindow(board.getPlannedDepartureAt(), alight.getPlannedArrivalAt());

        HorseLeg leg = new HorseLeg();
        leg.setBookingHorse(line);
        leg.setSequenceNumber(chain.stream().mapToInt(HorseLeg::getSequenceNumber).max().orElse(0) + 1);
        leg.setLegType(legType);
        leg.setFromLocation(board.getLocation());
        leg.setFromBookingAddress(board.getBookingAddress());
        leg.setToLocation(alight.getLocation());
        leg.setToBookingAddress(alight.getBookingAddress());
        leg.setVehicleTrip(trip);
        leg.setBoardTripStop(board);
        leg.setAlightTripStop(alight);
        if (failed != null && (failed.getLegType() == HorseLegType.DELIVERY || failed.getLegType() == HorseLegType.DIRECT) && legType == HorseLegType.DELIVERY) {
            leg.setReplacedHorseLeg(failed);
        }
        leg.setPlannedStartAt(board.getPlannedDepartureAt());
        leg.setPlannedEndAt(alight.getPlannedArrivalAt());
        leg.setStatus(HorseLegStatus.PLANNED);
        legs.saveAndFlush(leg);
        // Hệ thống tự xếp vị trí khoang ngay khi gán ngựa (NGHIEP_VU GĐ5), điều phối chỉnh ở sơ đồ khoang
        stallPlans.placeNew(leg);
        return horsesOnTrip(trip.getId());
    }

    @Override
    @Transactional
    public List<TripHorseResponseDto> cancel(Long horseLegId) {
        HorseLeg leg = legs.findWithTrip(horseLegId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chặng " + horseLegId));
        if (leg.getStatus() != HorseLegStatus.PLANNED) {
            throw new DispatchRuleException("status", "Chỉ gỡ được chặng chưa chạy.", true);
        }
        VehicleTrip trip = trips.findForUpdate(leg.getVehicleTrip().getId()).orElseThrow();
        requireNotStarted(trip);
        boolean hasLaterLeg = legs.findByBookingHorse_IdOrderBySequenceNumber(leg.getBookingHorse().getId()).stream()
                .anyMatch(l -> l.getSequenceNumber() > leg.getSequenceNumber() && !INACTIVE.contains(l.getStatus()));
        if (hasLaterLeg) {
            throw new DispatchRuleException("status", "Con ngựa còn chặng sau chặng này. Gỡ chặng sau trước.", true);
        }
        leg.setStatus(HorseLegStatus.CANCELLED);
        legs.flush();
        return horsesOnTrip(trip.getId());
    }

    private static void requireNotStarted(VehicleTrip trip) {
        if (trip.getStatus() != VehicleTripStatus.PLANNED && trip.getStatus() != VehicleTripStatus.DISPATCHED) {
            throw new DispatchRuleException("vehicleTripId", "Lượt xe đã chạy hoặc đã hủy, không đổi ngựa được.", true);
        }
    }

    private static TripStop stopOf(List<TripStop> tripStops, Long id) {
        return tripStops.stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
    }

    // Điểm lên, xuống là địa chỉ khách thì phải là địa chỉ của chính đơn này
    private static void requireAddressesOfBooking(Booking booking, TripStop board, TripStop alight) {
        for (TripStop stop : List.of(board, alight)) {
            if (stop.getBookingAddress() != null && !stop.getBookingAddress().getBooking().getId().equals(booking.getId())) {
                throw new DispatchRuleException("boardTripStopId", "Điểm dừng " + stop.getSequenceNumber() + " là địa chỉ của đơn khác.");
            }
        }
    }

    private void requireLegFitsBooking(HorseLegType legType, VehicleTrip trip, Booking booking, BookingHorse line, List<TripStop> tripStops,
                                       TripStop board, TripStop alight, Map<AddressRole, Long> addressIds) {
        Long pickup = addressIds.get(AddressRole.PICKUP);
        Long delivery = addressIds.get(AddressRole.DELIVERY);
        switch (legType) {
            case LINEHAUL -> {
                if (booking.getDeparture() == null || !booking.getDeparture().getId().equals(trip.getDeparture().getId())) {
                    throw new DispatchRuleException("vehicleTripId", "Đơn " + booking.getCode() + " không đi chuyến của lượt xe trục này.");
                }
                DispatchPolicy.requireLinehaulEnds(board.getSequenceNumber(), alight.getSequenceNumber(),
                        tripStops.getFirst().getSequenceNumber(), tripStops.getLast().getSequenceNumber());
                DispatchPolicy.requireSameLinehaulTrip(trip.getId(), legs.findLinehaulTripIdsOfBooking(booking.getId(), line.getId(), INACTIVE));
            }
            case PICKUP -> requireAddress(board, pickup, "Chặng đón phải lên xe ở địa chỉ đón của đơn.");
            case DELIVERY -> requireAddress(alight, delivery, "Chặng giao phải xuống xe ở địa chỉ giao của đơn.");
            case DIRECT -> {
                if (booking.getDeparture() != null) {
                    throw new DispatchRuleException("vehicleTripId", "Đơn " + booking.getCode() + " đi trục, không chạy thẳng được.");
                }
                requireAddress(board, pickup, "Chạy thẳng phải lên xe ở địa chỉ đón của đơn.");
                requireAddress(alight, delivery, "Chạy thẳng phải xuống xe ở địa chỉ giao của đơn.");
            }
            default -> {
                // TRANSFER: chỉ cần nối tiếp chuỗi chặng
            }
        }
    }

    private static void requireAddress(TripStop stop, Long addressId, String message) {
        if (stop.getBookingAddress() == null || !Objects.equals(stop.getBookingAddress().getId(), addressId)) {
            throw new DispatchRuleException("boardTripStopId", message);
        }
    }

    // Khóa so sánh nơi chốn: "L<id>" điểm mạng lưới, "A<id>" địa chỉ của đơn
    private static String placeKey(TripStop stop) {
        return stop.getLocation() != null ? "L" + stop.getLocation().getId() : "A" + stop.getBookingAddress().getId();
    }

    private static String endKey(HorseLeg leg) {
        return leg.getToLocation() != null ? "L" + leg.getToLocation().getId() : "A" + leg.getToBookingAddress().getId();
    }
}
