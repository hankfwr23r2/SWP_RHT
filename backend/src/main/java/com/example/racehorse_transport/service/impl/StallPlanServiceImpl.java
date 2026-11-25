package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.dispatch.StallPlanApplyRequestDto;
import com.example.racehorse_transport.dto.dispatch.StallPlanApplyRequestDto.StallAssignmentDto;
import com.example.racehorse_transport.dto.dispatch.StallPlanResponseDto;
import com.example.racehorse_transport.dto.dispatch.StallPlanResponseDto.StallHorseDto;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.StallPlanPolicy;
import com.example.racehorse_transport.policy.StallPlanPolicy.StallHorse;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.StallPlanService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StallPlanServiceImpl implements StallPlanService {

    private static final Set<HorseLegStatus> INACTIVE = Set.of(HorseLegStatus.CANCELLED, HorseLegStatus.FAILED);

    private final VehicleTripRepository trips;
    private final HorseLegRepository legs;

    @Override
    @Transactional(readOnly = true)
    public StallPlanResponseDto plan(Long vehicleTripId) {
        VehicleTrip trip = trips.findById(vehicleTripId).orElseThrow(() -> notFound(vehicleTripId));
        List<HorseLeg> onTrip = active(trip);
        return describe(trip, onTrip, current(onTrip));
    }

    @Override
    @Transactional(readOnly = true)
    public StallPlanResponseDto suggest(Long vehicleTripId) {
        VehicleTrip trip = trips.findById(vehicleTripId).orElseThrow(() -> notFound(vehicleTripId));
        requireEditable(trip);
        List<HorseLeg> onTrip = active(trip);
        return describe(trip, onTrip, StallPlanPolicy.suggest(horses(onTrip), segments(trip)));
    }

    @Override
    @Transactional
    public StallPlanResponseDto apply(Long vehicleTripId, StallPlanApplyRequestDto request) {
        // Khóa lượt xe: hai điều phối cùng lưu thì người sau thấy sơ đồ đã đổi (quy tắc #40)
        VehicleTrip trip = trips.findForUpdate(vehicleTripId).orElseThrow(() -> notFound(vehicleTripId));
        requireEditable(trip);
        List<HorseLeg> onTrip = active(trip);
        Map<Long, StallAssignmentDto> sent = request.horses().stream()
                .collect(Collectors.toMap(StallAssignmentDto::horseLegId, Function.identity(), (a, b) -> a));
        boolean sameHorses = sent.size() == request.horses().size() && sent.keySet().equals(onTrip.stream().map(HorseLeg::getId).collect(Collectors.toSet()));
        boolean samePositions = sameHorses && onTrip.stream().allMatch(leg -> Objects.equals(sent.get(leg.getId()).currentPosition(), position(leg)));
        if (!samePositions) {
            throw new DispatchRuleException("horses", "Sơ đồ khoang vừa thay đổi (thêm, bớt ngựa hoặc đổi chỗ). Tải lại sơ đồ mới rồi xếp lại.", true);
        }
        Map<Long, Integer> positions = new HashMap<>();
        sent.forEach((legId, entry) -> positions.put(legId, entry.stallPosition()));
        for (HorseLeg leg : onTrip) {
            if (locked(leg) && !Objects.equals(positions.get(leg.getId()), position(leg))) {
                throw new DispatchRuleException("horses", leg.getBookingHorse().getHorse().getName() + " đã lên xe, không đổi vị trí được.", true);
            }
        }
        StallPlanPolicy.requireValid(horses(onTrip), positions, segments(trip));
        store(onTrip, positions);
        return describe(trip, onTrip, positions);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void placeNew(HorseLeg leg) {
        VehicleTrip trip = leg.getVehicleTrip();
        List<HorseLeg> onTrip = active(trip);
        List<StallHorse> all = horses(onTrip);
        StallHorse added = all.stream().filter(h -> h.legId().equals(leg.getId())).findFirst().orElseThrow();
        List<StallHorse> others = all.stream().filter(h -> h != added).toList();
        Optional<Integer> spot = others.stream().allMatch(h -> h.position() != null)
                ? StallPlanPolicy.placeOne(added, others, segments(trip)) : Optional.empty();
        if (spot.isPresent()) {
            if (rearrangeWithoutBlocking(onTrip, leg, spot.get(), all, segments(trip))) return;
            leg.setStallPosition(spot.get().shortValue());
            legs.flush();
            return;
        }
        // Đủ chỗ nhưng lẻ vị trí (hoặc còn con chưa xếp): dồn các con chưa lên xe; không được thì từ chối và nêu con vướng
        store(onTrip, StallPlanPolicy.suggest(all, segments(trip)));
    }

    // Vị trí trống gần đầu xe làm con xuống trước bị chắn thì xếp lại, nếu cách xếp lại ít cảnh báo hơn
    private boolean rearrangeWithoutBlocking(List<HorseLeg> onTrip, HorseLeg added, int spot, List<StallHorse> all, List<Integer> segments) {
        Map<Long, Integer> positions = current(onTrip);
        positions.put(added.getId(), spot);
        int before = StallPlanPolicy.warnings(all, positions).size();
        if (before == 0) return false;
        Map<Long, Integer> suggested;
        try {
            suggested = StallPlanPolicy.suggest(all, segments);
        } catch (DispatchRuleException e) {
            return false;
        }
        if (StallPlanPolicy.warnings(all, suggested).size() >= before) return false;
        store(onTrip, suggested);
        return true;
    }

    private void store(List<HorseLeg> onTrip, Map<Long, Integer> positions) {
        onTrip.forEach(leg -> leg.setStallPosition(positions.get(leg.getId()).shortValue()));
        legs.flush();
    }

    private List<HorseLeg> active(VehicleTrip trip) {
        return legs.findActiveOnTrip(trip.getId(), INACTIVE);
    }

    private static Map<Long, Integer> current(List<HorseLeg> onTrip) {
        Map<Long, Integer> positions = new HashMap<>();
        onTrip.stream().filter(leg -> leg.getStallPosition() != null).forEach(leg -> positions.put(leg.getId(), position(leg)));
        return positions;
    }

    private static List<StallHorse> horses(List<HorseLeg> onTrip) {
        return onTrip.stream().map(leg -> new StallHorse(leg.getId(), leg.getBookingHorse().getHorse().getName(),
                leg.getBookingHorse().getBooking().getId(), leg.getBookingHorse().getHorse().getSex(),
                leg.getBoardTripStop().getSequenceNumber(), leg.getAlightTripStop().getSequenceNumber(),
                StallSeatPolicy.seats(leg.getBookingHorse().getStallType().getCode()), position(leg), locked(leg))).toList();
    }

    // Vách di động: cả xe là một dãy liền, đánh số từ 1 tới tổng chỗ
    private static List<Integer> segments(VehicleTrip trip) {
        Vehicle vehicle = trip.getVehicle();
        return List.of(vehicle.getStandardStallCount() * StallSeatPolicy.seats("STANDARD") + vehicle.getWideStallCount() * StallSeatPolicy.seats("WIDE"));
    }

    // Lên xe là khóa vị trí con đó
    private static boolean locked(HorseLeg leg) {
        return leg.getStatus() != HorseLegStatus.PLANNED;
    }

    private static Integer position(HorseLeg leg) {
        return leg.getStallPosition() == null ? null : leg.getStallPosition().intValue();
    }

    // Xe xuất phát thì khóa cả sơ đồ: sơ đồ soạn sẵn cho mọi con, kể cả con lên ở điểm dừng sau (người dùng chốt 24/11/2026)
    private static void requireEditable(VehicleTrip trip) {
        if (trip.getStatus() != VehicleTripStatus.PLANNED && trip.getStatus() != VehicleTripStatus.DISPATCHED) {
            throw new DispatchRuleException("vehicleTripId", "Lượt xe đã xuất phát hoặc đã kết thúc, sơ đồ khoang đã khóa.", true);
        }
    }

    private StallPlanResponseDto describe(VehicleTrip trip, List<HorseLeg> onTrip, Map<Long, Integer> positions) {
        List<StallHorseDto> rows = onTrip.stream().map(leg -> new StallHorseDto(leg.getId(), leg.getBookingHorse().getHorse().getName(),
                leg.getBookingHorse().getBooking().getCode(), leg.getBookingHorse().getHorse().getSex(),
                StallSeatPolicy.seats(leg.getBookingHorse().getStallType().getCode()),
                leg.getBoardTripStop().getSequenceNumber(), leg.getAlightTripStop().getSequenceNumber(),
                stopName(leg.getBoardTripStop()), stopName(leg.getAlightTripStop()), positions.get(leg.getId()), locked(leg))).toList();
        boolean editable = trip.getStatus() == VehicleTripStatus.PLANNED || trip.getStatus() == VehicleTripStatus.DISPATCHED;
        return new StallPlanResponseDto(trip.getId(), StallPlanPolicy.total(segments(trip)), editable, rows,
                StallPlanPolicy.warnings(horses(onTrip), positions));
    }

    private static String stopName(TripStop stop) {
        return stop.getLocation() != null ? stop.getLocation().getName() : stop.getBookingAddress().getAddressLine();
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Không tìm thấy lượt xe " + id);
    }
}
