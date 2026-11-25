package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.enums.CrewRole;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.policy.DispatchPolicy.Interval;
import com.example.racehorse_transport.policy.DispatchPolicy.Occupancy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DispatchPolicyTest {

    private static Instant at(String hhmm) {
        return Instant.parse("2026-12-07T" + hhmm + ":00Z");
    }

    private static Interval window(String from, String to) {
        return new Interval(at(from), at(to));
    }

    @Test
    void plannedEndMustBeAfterStart() {
        assertThatCode(() -> DispatchPolicy.requirePlannedWindow(at("06:00"), at("10:00"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> DispatchPolicy.requirePlannedWindow(at("10:00"), at("10:00")))
                .isInstanceOf(DispatchRuleException.class).hasMessageContaining("sau giờ bắt đầu");
    }

    @Test
    void routeStopWindowUsesTheArrivalOffsetAndDwell() {
        assertThat(DispatchPolicy.routeStopWindow(at("06:00"), 90, 30)).isEqualTo(window("07:30", "08:00"));
    }

    @Test
    void stopsMustBeOrderedInsideTheTrip() {
        Interval trip = window("06:00", "12:00");
        assertThatCode(() -> DispatchPolicy.requireStopsInOrder(trip,
                List.of(window("06:00", "06:30"), window("08:00", "08:45"), window("11:00", "11:00")))).doesNotThrowAnyException();

        assertThatThrownBy(() -> DispatchPolicy.requireStopsInOrder(trip, List.of(window("06:00", "06:30"))))
                .hasMessageContaining("ít nhất hai");
        assertThatThrownBy(() -> DispatchPolicy.requireStopsInOrder(trip, List.of(window("06:00", "06:30"), window("09:00", "08:00"))))
                .hasMessageContaining("rời trước giờ tới");
        assertThatThrownBy(() -> DispatchPolicy.requireStopsInOrder(trip, List.of(window("06:00", "07:00"), window("06:30", "08:00"))))
                .hasMessageContaining("tới trước khi rời");
        assertThatThrownBy(() -> DispatchPolicy.requireStopsInOrder(trip, List.of(window("05:30", "06:00"), window("08:00", "08:00"))))
                .hasMessageContaining("trước giờ xuất phát");
        assertThatThrownBy(() -> DispatchPolicy.requireStopsInOrder(trip, List.of(window("06:00", "06:30"), window("11:30", "12:30"))))
                .hasMessageContaining("sau giờ kết thúc");
    }

    @Test
    void nonLinehaulTripStartsAndEndsAtNetworkLocation() {
        assertThatCode(() -> DispatchPolicy.requireStartsAndEndsAtNetworkLocation(true, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> DispatchPolicy.requireStartsAndEndsAtNetworkLocation(false, true)).isInstanceOf(DispatchRuleException.class);
        assertThatThrownBy(() -> DispatchPolicy.requireStartsAndEndsAtNetworkLocation(true, false)).isInstanceOf(DispatchRuleException.class);
    }

    @ParameterizedTest
    @EnumSource(value = VehicleTripType.class, names = {"PICKUP", "DELIVERY", "DIRECT"})
    void localHorseTripMustEndAtAHubStopAtAHubLocation(VehicleTripType tripType) {
        assertThatCode(() -> DispatchPolicy.requireEndsAtHub(tripType, TripStopType.HUB, LocationType.HUB))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> DispatchPolicy.requireEndsAtHub(tripType, TripStopType.REST, LocationType.HUB))
                .isInstanceOf(DispatchRuleException.class);
        assertThatThrownBy(() -> DispatchPolicy.requireEndsAtHub(tripType, TripStopType.HUB, LocationType.REST_STOP))
                .isInstanceOf(DispatchRuleException.class);
    }

    @Test
    void transferAndLinehaulCanEndAtOtherNetworkLocations() {
        assertThatCode(() -> DispatchPolicy.requireEndsAtHub(VehicleTripType.TRANSFER, TripStopType.REST, LocationType.REST_STOP))
                .doesNotThrowAnyException();
        assertThatCode(() -> DispatchPolicy.requireEndsAtHub(VehicleTripType.LINEHAUL, TripStopType.BORDER, LocationType.BORDER_GATE))
                .doesNotThrowAnyException();
    }

    @Test
    void onlyActiveVehicleCanTakeNewTrip() {
        assertThatCode(() -> DispatchPolicy.requireActiveVehicle(VehicleStatus.ACTIVE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> DispatchPolicy.requireActiveVehicle(VehicleStatus.MAINTENANCE)).hasMessageContaining("MAINTENANCE");
        assertThatThrownBy(() -> DispatchPolicy.requireActiveVehicle(VehicleStatus.RETIRED)).hasMessageContaining("RETIRED");
    }

    @Test
    void vehicleOrStaffCannotBeOnTwoOverlappingTrips() {
        Interval planned = window("08:00", "12:00");
        assertThatCode(() -> DispatchPolicy.requireNoOverlap("vehicleId", "Xe", planned,
                List.of(window("04:00", "08:00"), window("12:00", "15:00")))).doesNotThrowAnyException();
        assertThatThrownBy(() -> DispatchPolicy.requireNoOverlap("vehicleId", "Xe", planned, List.of(window("11:00", "13:00"))))
                .isInstanceOf(DispatchRuleException.class).hasMessageContaining("Xe đã có lượt chạy khác trùng giờ");
        assertThatThrownBy(() -> DispatchPolicy.requireNoOverlap("staffId", "Nhân viên", planned, List.of(window("09:00", "10:00"))))
                .hasMessageContaining("Nhân viên");
    }

    @Test
    void agentBelongsToTheForeignCountryAndImportGateMatchesTheDeparture() {
        assertThatCode(() -> DispatchPolicy.requireAgentOfCountry("Đại lý", "KH", "KH")).doesNotThrowAnyException();
        assertThatThrownBy(() -> DispatchPolicy.requireAgentOfCountry("Đại lý Lào", "LA", "KH")).hasMessageContaining("Đại lý Lào");
        assertThatCode(() -> DispatchPolicy.requireGateOfDeparture(1L, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> DispatchPolicy.requireGateOfDeparture(1L, 1L, "Mộc Bài")).doesNotThrowAnyException();
        assertThatThrownBy(() -> DispatchPolicy.requireGateOfDeparture(2L, 1L, "Mộc Bài")).hasMessageContaining("Mộc Bài");
    }

    @Test
    void newTripStartsWherePreviousTripEnded() {
        assertThatCode(() -> DispatchPolicy.requireStartsWherePreviousEnded("startLocationId", "Xe", 1L, 1L, "Hub A")).doesNotThrowAnyException();
        assertThatThrownBy(() -> DispatchPolicy.requireStartsWherePreviousEnded("startLocationId", "Xe", 1L, 2L, "Hub B"))
                .hasMessageContaining("lượt trước kết thúc ở nơi khác (Hub B)");
    }

    @Test
    void onlyActiveStaffWithMatchingAccountRoleIsAssigned() {
        assertThatCode(() -> DispatchPolicy.requireActiveStaff(EmploymentStatus.ACTIVE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> DispatchPolicy.requireActiveStaff(EmploymentStatus.ON_LEAVE)).hasMessageContaining("ON_LEAVE");

        assertThatCode(() -> DispatchPolicy.requireCrewRole(CrewRole.DRIVER, "DRIVER")).doesNotThrowAnyException();
        assertThatCode(() -> DispatchPolicy.requireCrewRole(CrewRole.ESCORT, "ESCORT")).doesNotThrowAnyException();
        assertThatThrownBy(() -> DispatchPolicy.requireCrewRole(CrewRole.DRIVER, "ESCORT")).hasMessageContaining("tài xế");
        assertThatThrownBy(() -> DispatchPolicy.requireCrewRole(CrewRole.ESCORT, "COORDINATOR")).hasMessageContaining("hộ tống");
        assertThatThrownBy(() -> DispatchPolicy.requireCrewRole(CrewRole.VET, "SPECIALIST")).hasMessageContaining("thú y");
    }

    @Test
    void boardStopComesBeforeAlightStopOnTheSameTrip() {
        assertThatCode(() -> DispatchPolicy.requireBoardBeforeAlight(true, true, 1, 3)).doesNotThrowAnyException();
        assertThatThrownBy(() -> DispatchPolicy.requireBoardBeforeAlight(false, true, 1, 3)).hasMessageContaining("thuộc lượt xe này");
        assertThatThrownBy(() -> DispatchPolicy.requireBoardBeforeAlight(true, false, 1, 3)).hasMessageContaining("thuộc lượt xe này");
        assertThatThrownBy(() -> DispatchPolicy.requireBoardBeforeAlight(true, true, 3, 3)).hasMessageContaining("sau điểm lên");
        assertThatThrownBy(() -> DispatchPolicy.requireBoardBeforeAlight(true, true, 4, 2)).hasMessageContaining("sau điểm lên");
    }

    @Test
    void linehaulHorseBoardsAtFirstStopAndAlightsAtLastStop() {
        assertThatCode(() -> DispatchPolicy.requireLinehaulEnds(1, 5, 1, 5)).doesNotThrowAnyException();
        assertThatThrownBy(() -> DispatchPolicy.requireLinehaulEnds(2, 5, 1, 5)).hasMessageContaining("điểm đầu tuyến");
        assertThatThrownBy(() -> DispatchPolicy.requireLinehaulEnds(1, 4, 1, 5)).hasMessageContaining("điểm cuối tuyến");
    }

    @Test
    void seatsOnBoardNeverExceedVehicleSeatsOnAnySegment() {
        // Xe 4 chỗ: một khoang rộng (2 chỗ) đi suốt, hai con khoang thường nối tiếp nhau
        List<Occupancy> fits = List.of(new Occupancy(1, 4, 2), new Occupancy(1, 2, 1), new Occupancy(2, 4, 1), new Occupancy(3, 4, 1));
        assertThatCode(() -> DispatchPolicy.requireCapacity(fits, 4)).doesNotThrowAnyException();

        // Đoạn sau điểm 2: 2 + 1 + 1 + 1 = 5 chỗ
        List<Occupancy> over = List.of(new Occupancy(1, 4, 2), new Occupancy(1, 3, 1), new Occupancy(2, 4, 1), new Occupancy(2, 3, 1));
        assertThatThrownBy(() -> DispatchPolicy.requireCapacity(over, 4))
                .isInstanceOf(DispatchRuleException.class).hasMessageContaining("đoạn sau điểm dừng 2 sẽ cần 5 chỗ");
        assertThatCode(() -> DispatchPolicy.requireCapacity(List.of(), 1)).doesNotThrowAnyException();
    }

    @Test
    void horsesOfOneBookingRideTheSameLinehaulTrip() {
        assertThatCode(() -> DispatchPolicy.requireSameLinehaulTrip(10L, List.of())).doesNotThrowAnyException();
        assertThatCode(() -> DispatchPolicy.requireSameLinehaulTrip(10L, List.of(10L, 10L))).doesNotThrowAnyException();
        assertThatThrownBy(() -> DispatchPolicy.requireSameLinehaulTrip(10L, List.of(10L, 11L))).hasMessageContaining("cùng một lượt xe trục");
    }

    @Test
    void legStartsAfterPreviousLegEnded() {
        assertThatCode(() -> DispatchPolicy.requireAfterPreviousLeg(at("09:00"), at("09:00"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> DispatchPolicy.requireAfterPreviousLeg(at("09:00"), at("08:59")))
                .hasMessageContaining("trước khi chặng trước");
    }

    @Test
    void hubCheckinAllowsArrivalUpToTheConfiguredDeadline() {
        Instant deadline = DispatchPolicy.hubCheckinDeadline(at("09:00"), 4);
        assertThat(deadline).isEqualTo(at("05:00"));
        assertThatCode(() -> DispatchPolicy.requireAtHubBeforeDeadline(deadline.minusSeconds(1), deadline)).doesNotThrowAnyException();
        assertThatCode(() -> DispatchPolicy.requireAtHubBeforeDeadline(deadline, deadline)).doesNotThrowAnyException();
        assertThatThrownBy(() -> DispatchPolicy.requireAtHubBeforeDeadline(deadline.plusSeconds(1), deadline))
                .isInstanceOfSatisfying(DispatchRuleException.class, error -> {
                    assertThat(error.getField()).isEqualTo("alightTripStopId");
                    assertThat(error.isConflict()).isTrue();
                });
    }

    @Test
    void legStartsWherePreviousLegEnded() {
        assertThatCode(() -> DispatchPolicy.requireContinuous("L3", "L3")).doesNotThrowAnyException();
        assertThatThrownBy(() -> DispatchPolicy.requireContinuous("L3", "L4")).hasMessageContaining("chặng trước");
        assertThatThrownBy(() -> DispatchPolicy.requireContinuous("A7", "L7")).hasMessageContaining("chặng trước");
    }

    @Test
    void partnerRoleMustBeValidOnTheDay() {
        LocalDate day = LocalDate.of(2031, 3, 5);
        assertThat(DispatchPolicy.roleValidOn(LocalDate.of(2031, 1, 1), null, day)).isTrue();
        assertThat(DispatchPolicy.roleValidOn(day, day, day)).isTrue();
        assertThat(DispatchPolicy.roleValidOn(day.plusDays(1), null, day)).isFalse();
        assertThat(DispatchPolicy.roleValidOn(LocalDate.of(2031, 1, 1), day.minusDays(1), day)).isFalse();
    }
}
