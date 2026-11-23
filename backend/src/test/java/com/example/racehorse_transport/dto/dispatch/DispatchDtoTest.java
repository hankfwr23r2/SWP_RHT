package com.example.racehorse_transport.dto.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Role;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.StallType;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.CrewRole;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.HorseLegType;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DispatchDtoTest {

    private static final Instant START = Instant.parse("2031-03-03T06:00:00Z");
    private static final Instant END = Instant.parse("2031-03-03T09:00:00Z");

    @Test
    void createsTripWithoutCodeStatusOrActualTimes() {
        Vehicle vehicle = new Vehicle();
        Departure departure = new Departure();
        Location start = new Location();
        Location end = new Location();
        var request = new VehicleTripCreateRequestDto(VehicleTripType.LINEHAUL, 5L, 7L, null);

        VehicleTrip trip = request.toEntity(vehicle, departure, start, end, START, END);

        assertThat(trip.getId()).isNull();
        assertThat(trip.getCode()).isNull();
        assertThat(trip.getStatus()).isNull();
        assertThat(trip.getActualStartAt()).isNull();
        assertThat(trip.getActualEndAt()).isNull();
        assertThat(trip.getTripType()).isEqualTo(VehicleTripType.LINEHAUL);
        assertThat(trip.getVehicle()).isSameAs(vehicle);
        assertThat(trip.getDeparture()).isSameAs(departure);
        assertThat(trip.getStartLocation()).isSameAs(start);
        assertThat(trip.getEndLocation()).isSameAs(end);
        assertThat(trip.getPlannedStartAt()).isEqualTo(START);
        assertThat(trip.getPlannedEndAt()).isEqualTo(END);
        assertThat(request.stopsOrEmpty()).isEmpty();
    }

    @Test
    void createsStopWithoutActualTimes() {
        VehicleTrip trip = new VehicleTrip();
        BookingAddress address = new BookingAddress();
        var request = new TripStopRequestDto(null, 9L, TripStopType.PICKUP, START, END);

        TripStop stop = request.toEntity(trip, 2, null, address);

        assertThat(stop.getId()).isNull();
        assertThat(stop.getActualArrivalAt()).isNull();
        assertThat(stop.getActualDepartureAt()).isNull();
        assertThat(stop.getVehicleTrip()).isSameAs(trip);
        assertThat(stop.getSequenceNumber()).isEqualTo(2);
        assertThat(stop.getLocation()).isNull();
        assertThat(stop.getBookingAddress()).isSameAs(address);
        assertThat(stop.getStopType()).isEqualTo(TripStopType.PICKUP);
        assertThat(stop.getPlannedArrivalAt()).isEqualTo(START);
        assertThat(stop.getPlannedDepartureAt()).isEqualTo(END);
    }

    @Test
    void createsAssignmentWithoutStatusAndTrimsNote() {
        VehicleTrip trip = new VehicleTrip();
        Staff staff = new Staff();

        CrewAssignment assignment = new CrewAssignmentRequestDto(3L, CrewRole.DRIVER, "  Lái ca đêm ").toEntity(trip, staff);

        assertThat(assignment.getId()).isNull();
        assertThat(assignment.getStatus()).isNull();
        assertThat(assignment.getAssignedAt()).isNull();
        assertThat(assignment.getVehicleTrip()).isSameAs(trip);
        assertThat(assignment.getStaff()).isSameAs(staff);
        assertThat(assignment.getCrewRole()).isEqualTo(CrewRole.DRIVER);
        assertThat(assignment.getNote()).isEqualTo("Lái ca đêm");
        assertThat(new CrewAssignmentRequestDto(3L, CrewRole.ESCORT, "  ").toEntity(trip, staff).getNote()).isNull();
    }

    @Test
    void describesTripWithStopsCrewAndVehicleSeats() {
        VehicleTrip trip = trip();
        TripStop stop = new TripStop();
        stop.setId(11L);
        stop.setSequenceNumber(1);
        stop.setStopType(TripStopType.HUB);
        stop.setLocation(trip.getStartLocation());
        stop.setPlannedArrivalAt(START);
        stop.setPlannedDepartureAt(START);
        CrewAssignment assignment = assignment(trip);

        var response = VehicleTripResponseDto.from(trip, List.of(TripStopResponseDto.from(stop)), List.of(CrewAssignmentResponseDto.from(assignment)));

        assertThat(response.code()).isEqualTo("VT-ABCDEFGH");
        assertThat(response.routeCode()).isEqualTo("HCM-DN");
        // 4 khoang thường + 1 khoang rộng = 6 chỗ
        assertThat(response.vehicleSeats()).isEqualTo(6);
        assertThat(response.startLocationName()).isEqualTo("Hub HCM");
        assertThat(response.stops()).singleElement().satisfies(s -> {
            assertThat(s.locationName()).isEqualTo("Hub HCM");
            assertThat(s.bookingAddressId()).isNull();
        });
        assertThat(response.crew()).singleElement().satisfies(c -> {
            assertThat(c.staffCode()).isEqualTo("STF-1");
            assertThat(c.status()).isEqualTo(CrewAssignmentStatus.ASSIGNED);
        });
    }

    @Test
    void describesCustomerStopWithBookingCode() {
        Booking booking = new Booking();
        booking.setCode("BK-ABCDEFGH");
        BookingAddress address = new BookingAddress();
        address.setId(9L);
        address.setBooking(booking);
        address.setAddressLine("Trại Bình Dương");
        TripStop stop = new TripStop();
        stop.setBookingAddress(address);
        stop.setStopType(TripStopType.PICKUP);

        var response = TripStopResponseDto.from(stop);

        assertThat(response.locationId()).isNull();
        assertThat(response.bookingCode()).isEqualTo("BK-ABCDEFGH");
        assertThat(response.addressLine()).isEqualTo("Trại Bình Dương");
    }

    @Test
    void describesMyAssignmentAndCandidate() {
        CrewAssignment assignment = assignment(trip());

        var mine = MyCrewAssignmentResponseDto.from(assignment);
        assertThat(mine.tripCode()).isEqualTo("VT-ABCDEFGH");
        assertThat(mine.licensePlate()).isEqualTo("51C-12345");
        assertThat(mine.endLocationName()).isEqualTo("Hub Đà Nẵng");

        var candidate = CrewCandidateResponseDto.from(assignment.getStaff());
        assertThat(candidate.roleCode()).isEqualTo("DRIVER");
        assertThat(candidate.homeHubName()).isEqualTo("Hub HCM");
    }

    @Test
    void describesLegHorseOnTripAndBookingWaitingForDispatch() {
        VehicleTrip trip = trip();
        Booking booking = new Booking();
        booking.setId(20L);
        booking.setCode("BK-ABCDEFGH");
        booking.setDeparture(trip.getDeparture());
        BookingAddress delivery = new BookingAddress();
        delivery.setId(31L);
        delivery.setAddressLine("Trại Huế");
        BookingHorse line = new BookingHorse();
        line.setId(40L);
        line.setBooking(booking);
        Horse horse = new Horse();
        horse.setName("Sao Mai");
        horse.setMicrochip("985000000100002");
        line.setHorse(horse);
        StallType wide = new StallType();
        wide.setCode("WIDE");
        line.setStallType(wide);
        line.setStatus(BookingHorseStatus.ACTIVE);
        HorseLeg leg = new HorseLeg();
        leg.setId(50L);
        leg.setBookingHorse(line);
        leg.setSequenceNumber(3);
        leg.setLegType(HorseLegType.DELIVERY);
        leg.setStatus(HorseLegStatus.PLANNED);
        leg.setVehicleTrip(trip);
        leg.setFromLocation(trip.getEndLocation());
        leg.setToBookingAddress(delivery);
        leg.setPlannedStartAt(START);
        leg.setPlannedEndAt(END);

        var legDto = HorseLegResponseDto.from(leg);
        assertThat(legDto.fromName()).isEqualTo("Hub Đà Nẵng");
        assertThat(legDto.toName()).isEqualTo("Trại Huế");
        assertThat(legDto.vehicleTripCode()).isEqualTo("VT-ABCDEFGH");
        assertThat(legDto.boardTripStopId()).isNull();

        var onTrip = TripHorseResponseDto.from(leg);
        assertThat(onTrip.bookingCode()).isEqualTo("BK-ABCDEFGH");
        assertThat(onTrip.horseName()).isEqualTo("Sao Mai");
        assertThat(onTrip.seats()).isEqualTo(2);

        var waiting = DispatchBookingResponseDto.from(booking, null, delivery,
                List.of(DispatchBookingResponseDto.HorseDto.from(line, List.of(legDto))));
        assertThat(waiting.routeCode()).isEqualTo("HCM-DN");
        assertThat(waiting.pickupAddress()).isNull();
        assertThat(waiting.deliveryAddress().addressLine()).isEqualTo("Trại Huế");
        assertThat(waiting.horses()).singleElement().satisfies(h -> {
            assertThat(h.seats()).isEqualTo(2);
            assertThat(h.legs()).hasSize(1);
        });
    }

    private static VehicleTrip trip() {
        Location hcm = location(1L, "Hub HCM");
        Route route = new Route();
        route.setCode("HCM-DN");
        Departure departure = new Departure();
        departure.setId(5L);
        departure.setRoute(route);
        Vehicle vehicle = new Vehicle();
        vehicle.setId(7L);
        vehicle.setLicensePlate("51C-12345");
        vehicle.setStandardStallCount(4);
        vehicle.setWideStallCount(1);
        VehicleTrip trip = new VehicleTrip();
        trip.setId(3L);
        trip.setCode("VT-ABCDEFGH");
        trip.setTripType(VehicleTripType.LINEHAUL);
        trip.setStatus(VehicleTripStatus.PLANNED);
        trip.setDeparture(departure);
        trip.setVehicle(vehicle);
        trip.setStartLocation(hcm);
        trip.setEndLocation(location(2L, "Hub Đà Nẵng"));
        trip.setPlannedStartAt(START);
        trip.setPlannedEndAt(END);
        return trip;
    }

    private static CrewAssignment assignment(VehicleTrip trip) {
        Role role = new Role();
        role.setCode("DRIVER");
        AppUser user = new AppUser();
        user.setRole(role);
        Staff staff = new Staff();
        staff.setUserId(4L);
        staff.setUser(user);
        staff.setStaffCode("STF-1");
        staff.setFullName("Tài xế Một");
        staff.setHomeHubLocation(trip.getStartLocation());
        CrewAssignment assignment = new CrewAssignment();
        assignment.setId(8L);
        assignment.setVehicleTrip(trip);
        assignment.setStaff(staff);
        assignment.setCrewRole(CrewRole.DRIVER);
        assignment.setStatus(CrewAssignmentStatus.ASSIGNED);
        return assignment;
    }

    private static Location location(Long id, String name) {
        Location location = new Location();
        location.setId(id);
        location.setName(name);
        return location;
    }
}
