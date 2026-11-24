package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.CrewRole;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

// Quy tắc điều độ, hàm thuần (docs/DB_SCHEMA_59_BANG.md mục "Ràng buộc phải kiểm ở service").
// Service đọc dữ liệu rồi truyền vào; policy không đọc DB.
public final class DispatchPolicy {

    // Khoảng thời gian [start, end) của một lượt xe hoặc một điểm dừng
    public record Interval(Instant start, Instant end) {
        public boolean overlaps(Interval other) {
            return start.isBefore(other.end) && other.start.isBefore(end);
        }
    }

    // Một con ngựa trên xe: lên ở điểm dừng boardSequence, xuống ở alightSequence, chiếm seats chỗ
    public record Occupancy(int boardSequence, int alightSequence, int seats) {}

    private DispatchPolicy() {}

    public static void requirePlannedWindow(Instant plannedStartAt, Instant plannedEndAt) {
        if (!plannedEndAt.isAfter(plannedStartAt)) {
            throw new DispatchRuleException("plannedEndAt", "Giờ kết thúc phải sau giờ bắt đầu.");
        }
    }

    // Điểm dừng nằm trong lượt xe, theo đúng thứ tự, rời đi không sớm hơn lúc tới
    public static void requireStopsInOrder(Interval trip, List<Interval> stops) {
        if (stops.size() < 2) {
            throw new DispatchRuleException("stops", "Lượt xe cần ít nhất hai điểm dừng.");
        }
        Instant previousDeparture = trip.start();
        for (int i = 0; i < stops.size(); i++) {
            Interval stop = stops.get(i);
            if (stop.end().isBefore(stop.start())) {
                throw new DispatchRuleException("stops", "Điểm dừng " + (i + 1) + " có giờ rời trước giờ tới.");
            }
            if (stop.start().isBefore(previousDeparture)) {
                throw new DispatchRuleException("stops", "Điểm dừng " + (i + 1) + " tới trước khi rời điểm trước hoặc trước giờ xuất phát.");
            }
            previousDeparture = stop.end();
        }
        if (previousDeparture.isAfter(trip.end())) {
            throw new DispatchRuleException("stops", "Điểm dừng cuối kết thúc sau giờ kết thúc lượt xe.");
        }
    }

    // Lượt đón, giao, chạy thẳng, chuyển xe bắt đầu và kết thúc ở một điểm của mạng lưới (hub);
    // địa chỉ của khách nằm giữa
    public static void requireStartsAndEndsAtNetworkLocation(boolean firstIsLocation, boolean lastIsLocation) {
        if (!firstIsLocation || !lastIsLocation) {
            throw new DispatchRuleException("stops", "Lượt xe phải bắt đầu và kết thúc ở một điểm của mạng lưới.");
        }
    }

    // Quy tắc #36: chỉ gán xe ACTIVE vào lượt mới
    public static void requireActiveVehicle(VehicleStatus status) {
        if (status != VehicleStatus.ACTIVE) {
            throw new DispatchRuleException("vehicleId", "Xe đang " + status + ", không gán vào lượt mới được.", true);
        }
    }

    // Quy tắc #10: một xe hoặc một nhân viên không nằm trong hai lượt chạy trùng giờ
    public static void requireNoOverlap(String field, String subject, Interval planned, Collection<Interval> otherTrips) {
        if (otherTrips.stream().anyMatch(planned::overlaps)) {
            throw new DispatchRuleException(field, subject + " đã có lượt chạy khác trùng giờ.", true);
        }
    }

    // Quy tắc #20 (xe) và #10 (nhân viên): lượt mới bắt đầu ở nơi lượt trước kết thúc.
    // expectedLocationId là nơi kết thúc lượt liền trước, hoặc hub gốc khi chưa có lượt nào.
    public static void requireStartsWherePreviousEnded(String field, String subject, Long startLocationId, Long expectedLocationId) {
        if (!Objects.equals(startLocationId, expectedLocationId)) {
            throw new DispatchRuleException(field, subject + " không ở điểm xuất phát của lượt này: lượt trước kết thúc ở nơi khác.", true);
        }
    }

    public static void requireActiveStaff(EmploymentStatus status) {
        if (status != EmploymentStatus.ACTIVE) {
            throw new DispatchRuleException("staffId", "Nhân viên đang " + status + ", không phân công được.");
        }
    }

    // Tài xế phải là tài khoản DRIVER, hộ tống là ESCORT. Đợt này chưa có tài khoản thú y nên không phân công VET.
    public static void requireCrewRole(CrewRole crewRole, String accountRoleCode) {
        String expected = switch (crewRole) {
            case DRIVER -> "DRIVER";
            case ESCORT -> "ESCORT";
            case VET -> throw new DispatchRuleException("crewRole", "Chưa có tài khoản thú y để phân công vào lượt xe.");
        };
        if (!expected.equals(accountRoleCode)) {
            throw new DispatchRuleException("staffId", "Nhân viên này không phải " + (crewRole == CrewRole.DRIVER ? "tài xế" : "hộ tống") + ".");
        }
    }

    // Quy tắc #8: điểm lên và điểm xuống thuộc đúng lượt xe của chặng, lên trước xuống
    public static void requireBoardBeforeAlight(boolean boardOnTrip, boolean alightOnTrip, int boardSequence, int alightSequence) {
        if (!boardOnTrip || !alightOnTrip) {
            throw new DispatchRuleException("boardTripStopId", "Điểm lên và điểm xuống phải thuộc lượt xe này.");
        }
        if (boardSequence >= alightSequence) {
            throw new DispatchRuleException("alightTripStopId", "Điểm xuống phải sau điểm lên.");
        }
    }

    // Quy tắc #21: ngựa chỉ lên xe trục ở điểm đầu tuyến và xuống ở điểm cuối tuyến
    public static void requireLinehaulEnds(int boardSequence, int alightSequence, int firstSequence, int lastSequence) {
        if (boardSequence != firstSequence || alightSequence != lastSequence) {
            throw new DispatchRuleException("boardTripStopId", "Ngựa chỉ lên xe trục ở điểm đầu tuyến và xuống ở điểm cuối tuyến.");
        }
    }

    // Quy tắc #9: tại mọi đoạn giữa hai điểm dừng, tổng chỗ ngựa đang trên xe ≤ tổng chỗ của xe (khoang rộng 2 chỗ)
    public static void requireCapacity(Collection<Occupancy> occupancies, int vehicleSeats) {
        int lastStop = occupancies.stream().mapToInt(Occupancy::alightSequence).max().orElse(0);
        for (int segment = 1; segment < lastStop; segment++) {
            int at = segment;
            int used = occupancies.stream()
                    .filter(o -> o.boardSequence() <= at && at < o.alightSequence())
                    .mapToInt(Occupancy::seats).sum();
            if (used > vehicleSeats) {
                throw new DispatchRuleException("bookingHorseId",
                        "Xe chỉ có " + vehicleSeats + " chỗ, đoạn sau điểm dừng " + at + " sẽ cần " + used + " chỗ.", true);
            }
        }
    }

    // Quy tắc #30: mọi ngựa ACTIVE của một đơn đi cùng một lượt xe trục
    public static void requireSameLinehaulTrip(Long tripId, Collection<Long> linehaulTripIdsOfBooking) {
        if (linehaulTripIdsOfBooking.stream().anyMatch(other -> !other.equals(tripId))) {
            throw new DispatchRuleException("vehicleTripId", "Ngựa cùng đơn phải đi cùng một lượt xe trục.", true);
        }
    }

    // Chặng mới của con ngựa không bắt đầu trước khi chặng trước kết thúc
    public static void requireAfterPreviousLeg(Instant previousLegEnd, Instant nextLegStart) {
        if (nextLegStart.isBefore(previousLegEnd)) {
            throw new DispatchRuleException("boardTripStopId", "Chặng mới bắt đầu trước khi chặng trước của con ngựa kết thúc.", true);
        }
    }

    // Quy tắc #7: chặng mới bắt đầu đúng nơi chặng trước của con ngựa kết thúc.
    // Nơi được so bằng khóa "L<id>" (điểm mạng lưới) hoặc "A<id>" (địa chỉ của đơn).
    public static void requireContinuous(String previousEnd, String nextStart) {
        if (!Objects.equals(previousEnd, nextStart)) {
            throw new DispatchRuleException("boardTripStopId", "Chặng mới phải bắt đầu ở nơi chặng trước của con ngựa kết thúc.", true);
        }
    }

    // Quy tắc #26: đối tác chỉ được tham chiếu khi có vai trò đúng loại còn hiệu lực vào ngày đó
    public static boolean roleValidOn(LocalDate validFrom, LocalDate validTo, LocalDate day) {
        return (validFrom == null || !validFrom.isAfter(day)) && (validTo == null || !validTo.isBefore(day));
    }
}
