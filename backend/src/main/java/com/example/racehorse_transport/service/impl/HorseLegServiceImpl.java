package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.service.CoordinatorAssignmentService;
import com.example.racehorse_transport.policy.DepartureDelayPolicy;
import com.example.racehorse_transport.service.RedeliveryChargeService;
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
import com.example.racehorse_transport.policy.LegNeedsPolicy;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.policy.TripSchedulePolicy;
import com.example.racehorse_transport.repository.BookingAddressRepository;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.DispatchBookingRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.CustomerNoticeService;
import com.example.racehorse_transport.service.HorseLegService;
import com.example.racehorse_transport.service.StallPlanService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HorseLegServiceImpl implements HorseLegService {

    private static final Set<BookingStatus> DISPATCHABLE = Set.of(BookingStatus.CONFIRMED, BookingStatus.IN_OPERATION);
    // Chặng không còn tính vào chuỗi và sức chứa
    private static final Set<HorseLegStatus> INACTIVE = Set.of(HorseLegStatus.CANCELLED, HorseLegStatus.FAILED);

    private final HorseLegRepository legs;
    private final CoordinatorAssignmentService coordinators;
    private final TripStopRepository stops;
    private final BookingHorseRepository bookingHorses;
    private final BookingAddressRepository bookingAddresses;
    private final DispatchBookingRepository dispatchBookings;
    private final StallPlanService stallPlans;
    private final SystemConfigService configs;
    private final CustomerNoticeService notices;
    private final RedeliveryChargeService redeliveryCharges;

    @Override
    @Transactional(readOnly = true)
    public List<DispatchBookingResponseDto> dispatchableBookings(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new DispatchRuleException("to", "Khoảng ngày không hợp lệ.");
        }
        int hubCheckinHours = configs.getInt("HUB_CHECKIN_HOURS");
        List<DispatchBookingResponseDto> result = new ArrayList<>();
        for (Booking booking : dispatchBookings.findDispatchable(DISPATCHABLE, from, to)) {
            Map<AddressRole, BookingAddress> addresses = bookingAddresses.findByBooking_Id(booking.getId()).stream()
                    .collect(Collectors.toMap(BookingAddress::getAddressType, a -> a, (first, next) -> first));
            List<BookingHorse> lines = bookingHorses.findForDetail(booking.getId());
            Map<Long, List<HorseLegResponseDto>> legsByHorse = legs.findByBookingHorse_IdInOrderByBookingHorse_IdAscSequenceNumberAsc(
                            lines.stream().map(BookingHorse::getId).toList()).stream()
                    .collect(Collectors.groupingBy(l -> l.getBookingHorse().getId(), Collectors.mapping(HorseLegResponseDto::from, Collectors.toList())));
            List<HorseDto> horses = lines.stream().map(line -> {
                List<HorseLegResponseDto> own = legsByHorse.getOrDefault(line.getId(), List.of());
                return HorseDto.from(line, own, LegNeedsPolicy.missing(line.getStatus(), booking.getBookingType(), booking.getDeparture() != null,
                        line.getPickupMissChoice() == com.example.racehorse_transport.entity.enums.PickupMissChoice.SELF_DELIVERY
                                ? com.example.racehorse_transport.entity.enums.TransferMethod.SELF : booking.getPickupMethod(),
                        booking.getDeliveryMethod(),
                        own.stream().filter(leg -> !INACTIVE.contains(leg.status())).map(HorseLegResponseDto::legType).toList()));
            }).toList();
            var departure = booking.getDeparture();
            Instant departureAt = departure == null ? null : DepartureDelayPolicy.effectiveTime(departure);
            result.add(DispatchBookingResponseDto.from(booking, addresses.get(AddressRole.PICKUP), addresses.get(AddressRole.DELIVERY),
                    horses, departureAt, departureAt == null ? null : DispatchPolicy.hubCheckinDeadline(departureAt, hubCheckinHours)));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripHorseResponseDto> horsesOnTrip(Long vehicleTripId) {
        if (!coordinators.tripExists(vehicleTripId)) {
            throw new ResourceNotFoundException("Không tìm thấy lượt xe " + vehicleTripId);
        }
        // Gồm cả con giao thất bại: còn trên xe tới khi giao vào hub (B5)
        return legs.findActiveOnTrip(vehicleTripId, Set.of(HorseLegStatus.CANCELLED)).stream().map(TripHorseResponseDto::from).toList();
    }

    @Override
    @Transactional
    public List<TripHorseResponseDto> assign(Long vehicleTripId, HorseLegRequestDto request, String username) {
        // Khóa lượt xe: hai người gán ngựa cùng lúc không vượt sức chứa
        VehicleTrip trip = coordinators.requireTripWrite(vehicleTripId, username);
        requireNotStarted(trip);
        BookingHorse line = bookingHorses.findById(request.bookingHorseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa trong đơn " + request.bookingHorseId()));
        Booking booking = dispatchBookings.findForUpdate(line.getBooking().getId()).orElseThrow();
        HorseLegType legType = HorseLegType.valueOf(trip.getTripType().name());
        requireAssignable(line, booking, legType);
        List<TripStop> tripStops = stops.findByVehicleTrip_IdOrderBySequenceNumber(trip.getId());
        TripStop board = stopOf(tripStops, request.boardTripStopId());
        TripStop alight = stopOf(tripStops, request.alightTripStopId());
        Long pickupAddressId = requireStopsForLeg(legType, trip, booking, line, tripStops, board, alight);

        List<HorseLeg> chain = legs.findByBookingHorse_IdOrderBySequenceNumber(line.getId());
        if (chain.stream().anyMatch(l -> !INACTIVE.contains(l.getStatus()) && l.getVehicleTrip() != null && l.getVehicleTrip().getId().equals(trip.getId()))) {
            throw new DispatchRuleException("bookingHorseId", "Ngựa đã có trên lượt xe này.", true);
        }
        HorseLeg failed = requireAfterPredecessor(legType, booking, chain, board, pickupAddressId);
        List<Occupancy> occupancies = new ArrayList<>(legs.findActiveOnTrip(trip.getId(), INACTIVE).stream()
                .map(HorseLegServiceImpl::occupancy).toList());
        occupancies.add(occupancy(board, alight, line));
        requireCapacity(trip, occupancies);
        requireLegTimes(legType, trip, booking, tripStops, board, alight, failed);

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
        if (leg.getReplacedHorseLeg() != null) redeliveryCharges.chargeReadyTrip(leg);
        notices.legAssigned(leg);
        return horsesOnTrip(trip.getId());
    }

    @Override
    @Transactional
    public List<TripHorseResponseDto> cancel(Long horseLegId, String username) {
        HorseLeg leg = legs.findWithTrip(horseLegId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chặng " + horseLegId));
        if (leg.getStatus() != HorseLegStatus.PLANNED) {
            throw new DispatchRuleException("status", "Chỉ gỡ được chặng chưa chạy.", true);
        }
        VehicleTrip trip = coordinators.requireTripWrite(leg.getVehicleTrip().getId(), username);
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

    @Override
    @Transactional
    public int cancelPlannedLegsOfBooking(Long bookingId) {
        List<HorseLeg> planned = legs.findByBookingHorse_Booking_IdAndStatus(bookingId, HorseLegStatus.PLANNED);
        planned.forEach(HorseLegServiceImpl::cancelPlanned);
        notices.clearHubAppointmentsAndNotify(bookingId, planned.size());
        legs.flush();
        return planned.size();
    }

    @Override
    @Transactional
    public List<Long> cancelPlannedLegsAfter(List<HorseLeg> affectedLegs) {
        affectedLegs.stream().map(leg -> leg.getBookingHorse().getBooking().getId()).distinct().sorted()
                .forEach(bookingId -> dispatchBookings.findForUpdate(bookingId).orElseThrow());
        List<HorseLeg> planned = affectedLegs.stream()
                .flatMap(leg -> legs.findByBookingHorse_IdOrderBySequenceNumber(leg.getBookingHorse().getId()).stream()
                        .filter(next -> next.getSequenceNumber() > leg.getSequenceNumber() && next.getStatus() == HorseLegStatus.PLANNED))
                .toList();
        planned.forEach(HorseLegServiceImpl::cancelPlanned);
        legs.flush();
        return planned.stream().map(HorseLeg::getId).toList();
    }

    private static void cancelPlanned(HorseLeg leg) {
        leg.setStatus(HorseLegStatus.CANCELLED);
        leg.setStallPosition(null);
    }

@Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<HorseLeg> replanTrip(VehicleTrip trip, List<TripStop> tripStops) {
        // Khóa đơn trước khi đọc chặng: đổi chuyến/hủy đơn có thể đồng thời gỡ chặng kế hoạch.
        legs.findActiveBookingIdsOnTrip(trip.getId(), INACTIVE)
                .forEach(bookingId -> dispatchBookings.findForUpdate(bookingId).orElseThrow());
        List<HorseLeg> onTrip = legs.findActiveOnTrip(trip.getId(), INACTIVE);
        for (HorseLeg leg : onTrip) {
            if (leg.getStatus() != HorseLegStatus.PLANNED || leg.getActualStartAt() != null || leg.getActualEndAt() != null) {
                throw new DispatchRuleException("status", "Lượt xe đã có chặng bắt đầu, không sửa được.", true);
            }
            BookingHorse line = leg.getBookingHorse();
            Booking booking = line.getBooking();
            var type = leg.getLegType();
            requireAssignable(line, booking, type);
            var board = leg.getBoardTripStop();
            var alight = leg.getAlightTripStop();
            Long pickupAddressId = requireStopsForLeg(type, trip, booking, line, tripStops, board, alight);
            var chain = legs.findByBookingHorse_IdOrderBySequenceNumber(line.getId());
            var before = chain.stream().filter(other -> other.getSequenceNumber() < leg.getSequenceNumber()).toList();
            HorseLeg failed = requireAfterPredecessor(type, booking, before, board, pickupAddressId);
            requireLegTimes(type, trip, booking, tripStops, board, alight, failed);
            chain.stream().filter(other -> other.getSequenceNumber() > leg.getSequenceNumber() && !INACTIVE.contains(other.getStatus()))
                    .findFirst().ifPresent(next -> {
                        DispatchPolicy.requireContinuous(placeKey(alight), startKey(next));
                        DispatchPolicy.requireAfterPreviousLeg(alight.getPlannedArrivalAt(), next.getPlannedStartAt());
                    });
        }
        requireCapacity(trip, onTrip.stream().map(HorseLegServiceImpl::occupancy).toList());
        onTrip.forEach(leg -> {
            leg.setPlannedStartAt(leg.getBoardTripStop().getPlannedDepartureAt());
            leg.setPlannedEndAt(leg.getAlightTripStop().getPlannedArrivalAt());
        });
        return onTrip;
    }

    private static void requireAssignable(BookingHorse line, Booking booking, HorseLegType legType) {
        if (line.getStatus() != BookingHorseStatus.ACTIVE) {
            throw new DispatchRuleException("bookingHorseId", "Ngựa này đang " + line.getStatus() + ", không xếp xe được.", true);
        }
        if (line.getPickupMissChoice() == com.example.racehorse_transport.entity.enums.PickupMissChoice.DROP_HORSE) {
            throw new DispatchRuleException("bookingHorseId", "Khách đã chọn bỏ con ngựa này khỏi đơn.", true);
        }
        if (!DISPATCHABLE.contains(booking.getStatus())) {
            throw new DispatchRuleException("bookingHorseId", "Đơn " + booking.getCode() + " đang " + booking.getStatus() + ", chưa xếp xe được.", true);
        }
        if (legType == HorseLegType.PICKUP && line.getPickupMissChoice()
                == com.example.racehorse_transport.entity.enums.PickupMissChoice.SELF_DELIVERY) {
            throw new DispatchRuleException("bookingHorseId", "Khách đã chọn tự chở con này tới hub.", true);
        }
    }

    private Long requireStopsForLeg(HorseLegType legType, VehicleTrip trip, Booking booking, BookingHorse line,
                                    List<TripStop> tripStops, TripStop board, TripStop alight) {
        DispatchPolicy.requireBoardBeforeAlight(board != null && trip.getId().equals(board.getVehicleTrip().getId()),
                alight != null && trip.getId().equals(alight.getVehicleTrip().getId()),
                board == null ? 0 : board.getSequenceNumber(), alight == null ? 0 : alight.getSequenceNumber());
        requireAddressesOfBooking(booking, board, alight);
        Map<AddressRole, Long> addressIds = bookingAddresses.findByBooking_Id(booking.getId()).stream()
                .collect(Collectors.toMap(BookingAddress::getAddressType, BookingAddress::getId, (first, next) -> first));
        if (line.getDeliveryAddress() != null) {
            addressIds.put(AddressRole.DELIVERY, line.getDeliveryAddress().getId());
        }
        requireLegFitsBooking(legType, trip, booking, line, tripStops, board, alight, addressIds);
        return addressIds.get(AddressRole.PICKUP);
    }

    private HorseLeg requireAfterPredecessor(HorseLegType legType, Booking booking, List<HorseLeg> chain,
                                             TripStop board, Long pickupAddressId) {
        HorseLeg previous = chain.stream().filter(l -> !INACTIVE.contains(l.getStatus())).reduce((first, second) -> second).orElse(null);
        if (legType == HorseLegType.DELIVERY && booking.getBookingType()
                == com.example.racehorse_transport.entity.enums.BookingType.IMPORT
                && booking.getStatus() == BookingStatus.CONFIRMED
                && (previous == null || previous.getStatus() != HorseLegStatus.COMPLETED
                    || dispatchBookings.hasPendingBalance(booking.getId()))) {
            throw new DispatchRuleException("payment", "Chỉ xếp giao đơn nhập khi ngựa đã tới hub và khách đã trả số dư.", true);
        }
        HorseLeg last = chain.stream().filter(existing -> existing.getStatus() != HorseLegStatus.CANCELLED)
                .reduce((first, next) -> next).orElse(null);
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
            DispatchPolicy.requireContinuous("A" + pickupAddressId, placeKey(board));
        }
        return failed;
    }

    private void requireLegTimes(HorseLegType legType, VehicleTrip trip, Booking booking, List<TripStop> tripStops,
                                 TripStop board, TripStop alight, HorseLeg failed) {
        DispatchPolicy.requirePlannedWindow(board.getPlannedDepartureAt(), alight.getPlannedArrivalAt());
        if (legType != HorseLegType.LINEHAUL) {
            int maxDriveHoursPerDay = configs.getInt("MAX_DRIVE_HOURS_PER_DAY");
            var rules = new TripSchedulePolicy.Rules(0, 0, maxDriveHoursPerDay, 0, 0);
            TripSchedulePolicy.requirePlannedRideWithinLimit(board.getPlannedDepartureAt(), alight.getPlannedArrivalAt(),
                    legType == HorseLegType.DIRECT, rules);
            if (legType == HorseLegType.DELIVERY && failed == null) {
                requireSharedDeliveryTrip(booking, trip, tripStops, board, maxDriveHoursPerDay);
            }
        }
        if (legType == HorseLegType.PICKUP && booking.getDeparture() != null) {
            DispatchPolicy.requireAtHubBeforeDeadline(alight.getPlannedArrivalAt(),
                    DispatchPolicy.hubCheckinDeadline(DepartureDelayPolicy.effectiveTime(booking.getDeparture()), configs.getInt("HUB_CHECKIN_HOURS")));
        }
    }

    private static Occupancy occupancy(HorseLeg leg) {
        return occupancy(leg.getBoardTripStop(), leg.getAlightTripStop(), leg.getBookingHorse());
    }

    private static Occupancy occupancy(TripStop board, TripStop alight, BookingHorse line) {
        return new Occupancy(board.getSequenceNumber(), alight.getSequenceNumber(), StallSeatPolicy.seats(line.getStallType().getCode()));
    }

    private static void requireCapacity(VehicleTrip trip, List<Occupancy> occupancies) {
        DispatchPolicy.requireCapacity(occupancies, trip.getVehicle().getStandardStallCount() * StallSeatPolicy.seats("STANDARD")
                + trip.getVehicle().getWideStallCount() * StallSeatPolicy.seats("WIDE"));
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
    private void requireSharedDeliveryTrip(Booking booking, VehicleTrip trip, List<TripStop> tripStops, TripStop board, int maxDriveHoursPerDay) {
        List<BookingHorse> active = bookingHorses.findForDetail(booking.getId()).stream()
                .filter(horse -> horse.getStatus() == BookingHorseStatus.ACTIVE).toList();
        var destinations = active.stream().map(BookingHorse::getDeliveryAddress).filter(Objects::nonNull)
                .map(BookingAddress::getId).collect(Collectors.toSet());
        if (destinations.size() < 2) return;
        var plannedDestinations = tripStops.stream().map(TripStop::getBookingAddress).filter(Objects::nonNull)
                .map(BookingAddress::getId).collect(Collectors.toSet());
        if (!plannedDestinations.containsAll(destinations)) {
            throw new DispatchRuleException("bookingHorseId", "Lượt giao phải có đủ nơi giao của đơn.", true);
        }
        Long destinationHub = booking.getDeparture().getRoute().getDestination().getId();
        if (board.getLocation() == null || !destinationHub.equals(board.getLocation().getId())
                || trip.getEndLocation() == null || !destinationHub.equals(trip.getEndLocation().getId())) {
            throw new DispatchRuleException("places", "Lượt giao nhiều nơi phải xuất phát và quay về hub đích.", true);
        }
        var rules = new TripSchedulePolicy.Rules(
                configs.getInt("MAX_CONTINUOUS_HOURS"), configs.getInt("MIN_REST_MINUTES"), maxDriveHoursPerDay, 0, 0);
        TripSchedulePolicy.requireMultiPlaceDrivingLimit(
                tripStops.stream().map(stop -> new DispatchPolicy.Interval(stop.getPlannedArrivalAt(), stop.getPlannedDepartureAt())).toList(), rules);
        boolean alreadyAssigned = legs.findByBookingHorse_IdInOrderByBookingHorse_IdAscSequenceNumberAsc(
                        active.stream().map(BookingHorse::getId).toList()).stream()
                .anyMatch(leg -> leg.getLegType() == HorseLegType.DELIVERY && !INACTIVE.contains(leg.getStatus())
                        && leg.getVehicleTrip() != null && !leg.getVehicleTrip().getId().equals(trip.getId()));
        if (alreadyAssigned) {
            throw new DispatchRuleException("bookingHorseId", "Các con trong đơn nhiều nơi giao phải đi chung một lượt giao.", true);
        }
    }

    private static String placeKey(TripStop stop) {
        return stop.getLocation() != null ? "L" + stop.getLocation().getId() : "A" + stop.getBookingAddress().getId();
    }

    private static String endKey(HorseLeg leg) {
        return leg.getToLocation() != null ? "L" + leg.getToLocation().getId() : "A" + leg.getToBookingAddress().getId();
    }

    private static String startKey(HorseLeg leg) {
        return leg.getFromLocation() != null ? "L" + leg.getFromLocation().getId() : "A" + leg.getFromBookingAddress().getId();
    }
}
