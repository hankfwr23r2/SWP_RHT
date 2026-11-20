package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.fleet.VehicleRequestDto;
import com.example.racehorse_transport.dto.fleet.VehicleResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingAssignment;
import com.example.racehorse_transport.entity.BookingVehicle;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.repository.BookingAssignmentRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingVehicleRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FleetServiceImplTest {

    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final StaffRepository staffRepository = mock(StaffRepository.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final BookingVehicleRepository bookingVehicleRepository = mock(BookingVehicleRepository.class);
    private final BookingAssignmentRepository bookingAssignmentRepository = mock(BookingAssignmentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private FleetServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FleetServiceImpl(
                vehicleRepository,
                staffRepository,
                bookingRepository,
                bookingVehicleRepository,
                bookingAssignmentRepository,
                userRepository);
    }

    @Test
    void updateVehicle_rejectsBusyVehicleStatus() {
        Vehicle vehicle = vehicle(1, "IN_USE");
        when(vehicleRepository.findById(1)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> service.updateVehicle("1", request(4, "IN_USE")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("không thể chỉnh sửa");

        verify(vehicleRepository, never()).save(vehicle);
    }

    @Test
    void updateVehicle_rejectsVehicleAssignedToHoldingBooking() {
        Vehicle vehicle = vehicle(1, "AVAILABLE");
        Booking booking = Booking.builder().id(42).status("in_transit").build();
        BookingVehicle assignment = BookingVehicle.builder().vehicleID(vehicle).bookingID(booking).build();
        when(vehicleRepository.findById(1)).thenReturn(Optional.of(vehicle));
        when(bookingVehicleRepository.findByVehicleID_Id(1)).thenReturn(List.of(assignment));

        assertThatThrownBy(() -> service.updateVehicle("1", request(4, "AVAILABLE")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("không thể chỉnh sửa");

        verify(vehicleRepository, never()).save(vehicle);
    }

    @Test
    void updateVehicle_allowsChangingCapacityWhenAvailable() {
        Vehicle vehicle = vehicle(1, "AVAILABLE");
        when(vehicleRepository.findById(1)).thenReturn(Optional.of(vehicle));
        when(bookingVehicleRepository.findByVehicleID_Id(1)).thenReturn(List.of());
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        VehicleResponseDto response = service.updateVehicle("1", request(4, "MAINTENANCE"));

        assertThat(response.getCapacity()).isEqualTo(4);
        assertThat(response.getStatus()).isEqualTo("AVAILABLE");
        assertThat(vehicle.getVehicleType()).isEqualTo("Xe chuyên dụng 4 ngăn");
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void updateVehicle_allowsChangingCapacityWhenUnderMaintenance() {
        Vehicle vehicle = vehicle(1, "MAINTENANCE");
        when(vehicleRepository.findById(1)).thenReturn(Optional.of(vehicle));
        when(bookingVehicleRepository.findByVehicleID_Id(1)).thenReturn(List.of());
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        VehicleResponseDto response = service.updateVehicle("1", request(6, "AVAILABLE"));

        assertThat(response.getCapacity()).isEqualTo(6);
        assertThat(response.getStatus()).isEqualTo("MAINTENANCE");
        assertThat(vehicle.getVehicleType()).isEqualTo("Xe chuyên dụng 6 ngăn");
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void validateCrewAvailability_holdsUnreturnedDriverOnlyForOrdersTheyCannotReach() {
        Staff driver = driver();
        Booking completed = unreturnedBooking("completed", "\"homeReturn\":{\"eta\":1000}");
        when(staffRepository.findById(1)).thenReturn(Optional.of(driver));
        when(bookingRepository.findAll()).thenReturn(List.of(completed));

        // Quá giờ về mà chưa xác nhận: không nhận đơn đi ngay
        assertThatThrownBy(() -> service.validateCrewAvailability(1, Instant.now().plusSeconds(86400),
                "domestic", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BK-8G4M2RWT")
                .hasMessageContaining("Đang về trụ sở")
                .hasMessageContaining("đã quá giờ dự kiến về");
        // Đơn ở xa vẫn nhận được
        service.validateCrewAvailability(1, Instant.now().plusSeconds(30L * 86400), "international", null);
    }

    @Test
    void getAvailableCrew_holdsUnreturnedDriverUntilTheyCouldBeBack() {
        Staff driver = driver();
        when(staffRepository.findByRoleIgnoreCase("DRIVER")).thenReturn(List.of(driver));
        when(bookingRepository.findAll()).thenReturn(List.of(
                unreturnedBooking("completed", "\"homeReturn\":{\"eta\":1000}")));
        when(bookingAssignmentRepository.findByBookingID_Id(43)).thenReturn(List.of());

        var soon = service.getAvailableCrew(Instant.now().plusSeconds(86400), "DRIVER", "domestic", null);
        assertThat(soon).hasSize(1);
        assertThat(soon.get(0).isAvailable()).isFalse();
        assertThat(soon.get(0).getBusyReason()).contains("BK-8G4M2RWT");

        var far = service.getAvailableCrew(Instant.now().plusSeconds(30L * 86400), "DRIVER", "domestic", null);
        assertThat(far.get(0).isAvailable()).isTrue();
    }

    @Test
    void validateCrewAvailability_activeTripBlocksOnlyOverlappingOrders() {
        Staff driver = driver();
        when(staffRepository.findById(1)).thenReturn(Optional.of(driver));
        when(bookingRepository.findAll()).thenReturn(List.of(unreturnedBooking("in_transit", "\"departedAt\":100")));

        assertThatThrownBy(() -> service.validateCrewAvailability(1, Instant.now().plusSeconds(86400),
                "domestic", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Đang đi đơn BK-8G4M2RWT");
        service.validateCrewAvailability(1, Instant.now().plusSeconds(30L * 86400), "international", null);
    }

    private Staff driver() {
        Staff staff = new Staff();
        staff.setId(1);
        staff.setRole("DRIVER");
        staff.setStaffCode("DR-01");
        staff.setEmploymentStatus("ACTIVE");
        return staff;
    }

    private Booking unreturnedBooking(String status, String tripFields) {
        return Booking.builder()
                .id(43)
                .code("BK-8G4M2RWT")
                .status(status)
                .detailsJson("{\"trips\":[{\"tripId\":\"TRP-043-1\",\"driverId\":\"DR-01\","
                        + tripFields + "}]}")
                .build();
    }

    @Test
    void unreturnedVehicleIsHeldAfterFinancialCompletionOnlyUntilItCouldBeBack() {
        Vehicle v = vehicle(1, "AVAILABLE");
        when(vehicleRepository.findAll()).thenReturn(List.of(v));
        when(vehicleRepository.findById(1)).thenReturn(Optional.of(v));
        Booking b = Booking.builder().id(43).code("BK-8G4M2RWT").status("completed")
                .detailsJson("{\"trips\":[{\"vehicleId\":\"VH-001\",\"homeReturn\":{\"eta\":1000}}]}").build();
        when(bookingRepository.findAll()).thenReturn(List.of(b));
        assertThat(service.getAllVehicles().getFirst().isAvailable()).isFalse();
        assertThat(service.getAllVehicles().getFirst().isOnTrip()).isTrue();
        // Chưa xác nhận về: khóa đơn đi ngay, đơn ở xa vẫn nhận (rủi ro về trễ xử lý bằng cờ "Về trễ")
        assertThat(service.getAvailableVehicles(Instant.now().plusSeconds(90L * 86400), "domestic", null)
                .getFirst().isAvailable()).isTrue();
        assertThatThrownBy(() -> service.validateVehicleAvailability(1, Instant.now(), "domestic", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("BK-8G4M2RWT");

        b.setDetailsJson("{\"trips\":[{\"vehicleId\":\"VH-001\",\"homeReturn\":{\"backAt\":2000}}]}");
        assertThat(service.getAllVehicles().getFirst().isAvailable()).isTrue();
        assertThat(service.getAvailableVehicles(Instant.now(), "domestic", null).getFirst().isAvailable()).isTrue();
        service.validateVehicleAvailability(1, Instant.now(), "domestic", null);
    }

    @Test
    void onlyTheVehicleWithAnUnreturnedTripIsHeld() {
        when(vehicleRepository.findAll()).thenReturn(List.of(vehicle(1, "AVAILABLE"), vehicle(2, "AVAILABLE")));
        Booking b = Booking.builder().id(43).code("BK-8G4M2RWT").status("settlement_issued")
                .detailsJson("{\"trips\":[{\"vehicleId\":\"1\",\"homeReturn\":{\"backAt\":2000}},"
                        + "{\"vehicleId\":\"VH-002\",\"homeReturn\":{\"eta\":3000}}]}").build();
        when(bookingRepository.findAll()).thenReturn(List.of(b));
        assertThat(service.getAllVehicles()).extracting("available").containsExactly(true, false);
    }

    private static final long DAY = 86400L;

    private Booking scheduled(int id, String code, String status, String type, long departInDays) {
        return Booking.builder().id(id).code(code).status(status).bookingType(type)
                .departureDate(Instant.now().plusSeconds(departInDays * DAY)).build();
    }

    private void holdVehicle(Vehicle v, Booking b) {
        when(bookingVehicleRepository.findByBookingID_Id(b.getId()))
                .thenReturn(List.of(BookingVehicle.builder().vehicleID(v).bookingID(b).build()));
    }

    @Test
    void vehicleHeldForFarBookingStaysSelectableForOtherDates() {
        Vehicle v = vehicle(1, "AVAILABLE");
        Booking far = scheduled(50, "BK-FAR00001", "under_review", "domestic", 20);
        when(vehicleRepository.findAll()).thenReturn(List.of(v));
        when(vehicleRepository.findById(1)).thenReturn(Optional.of(v));
        when(bookingRepository.findAll()).thenReturn(List.of(far));
        holdVehicle(v, far);

        VehicleResponseDto tomorrow = service.getAvailableVehicles(Instant.now().plusSeconds(DAY), "domestic", null).getFirst();
        assertThat(tomorrow.isAvailable()).isTrue();
        assertThat(tomorrow.getUpcoming()).extracting("bookingCode").containsExactly("BK-FAR00001");
        service.validateVehicleAvailability(1, Instant.now().plusSeconds(DAY), "domestic", null);

        VehicleResponseDto sameDay = service.getAvailableVehicles(Instant.now().plusSeconds(21 * DAY), "domestic", null).getFirst();
        assertThat(sameDay.isAvailable()).isFalse();
        assertThat(sameDay.getBusyBookingCode()).isEqualTo("BK-FAR00001");
        assertThat(sameDay.getBusyReason()).contains("Trùng lịch với đơn BK-FAR00001");
        assertThatThrownBy(() -> service.validateVehicleAvailability(1, Instant.now().plusSeconds(21 * DAY), "domestic", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(sameDay.getBusyReason());
    }

    @Test
    void bookingBeingPlannedDoesNotBlockItsOwnVehicle() {
        Vehicle v = vehicle(1, "AVAILABLE");
        Booking own = scheduled(50, "BK-OWN00001", "under_review", "domestic", 20);
        when(vehicleRepository.findAll()).thenReturn(List.of(v));
        when(bookingRepository.findAll()).thenReturn(List.of(own));
        when(bookingRepository.findIdByCode("BK-OWN00001")).thenReturn(Optional.of(50));
        holdVehicle(v, own);

        assertThat(service.getAvailableVehicles(own.getDepartureDate(), "domestic", "BK-OWN00001")
                .getFirst().isAvailable()).isTrue();
        assertThat(service.getAvailableVehicles(own.getDepartureDate(), "domestic", null)
                .getFirst().isAvailable()).isFalse();
    }

    @Test
    void fleetListSeparatesAssignedFromOnTrip() {
        Vehicle assigned = vehicle(1, "AVAILABLE");
        Vehicle onRoad = vehicle(2, "AVAILABLE");
        Booking upcoming = scheduled(50, "BK-UPC00001", "awaiting_payment", "domestic", 10);
        Booking rolling = scheduled(51, "BK-ROL00001", "in_transit", "international", -1);
        rolling.setDetailsJson("{\"trips\":[{\"vehicleId\":\"VH-002\",\"departedAt\":100}]}");
        when(vehicleRepository.findAll()).thenReturn(List.of(assigned, onRoad));
        when(bookingRepository.findAll()).thenReturn(List.of(upcoming, rolling));
        holdVehicle(assigned, upcoming);
        holdVehicle(onRoad, rolling);

        List<VehicleResponseDto> fleet = service.getAllVehicles();

        assertThat(fleet.get(0).isAvailable()).isFalse();
        assertThat(fleet.get(0).isOnTrip()).isFalse();
        assertThat(fleet.get(0).getStatus()).isEqualTo("AVAILABLE");
        assertThat(fleet.get(0).getBusyReason()).contains("Đã nhận đơn BK-UPC00001");
        assertThat(fleet.get(1).isOnTrip()).isTrue();
        assertThat(fleet.get(1).getOnTripBookingCode()).isEqualTo("BK-ROL00001");
        assertThat(fleet.get(1).getUpcoming()).isEmpty();
        // Đang lăn bánh: khóa đơn trùng khoảng giữ, đơn ở xa vẫn nhận được
        assertThat(service.getAvailableVehicles(Instant.now().plusSeconds(DAY), "domestic", null)
                .get(1).isAvailable()).isFalse();
        assertThat(service.getAvailableVehicles(Instant.now().plusSeconds(60 * DAY), "domestic", null)
                .get(1).isAvailable()).isTrue();
    }

    @Test
    void crewListUsesTripTypeLikeSaveValidation() {
        Staff driver = driver();
        Booking inter = scheduled(60, "BK-INT00001", "awaiting_payment", "international", 10);
        when(staffRepository.findByRoleIgnoreCase("DRIVER")).thenReturn(List.of(driver));
        when(staffRepository.findById(1)).thenReturn(Optional.of(driver));
        when(bookingRepository.findAll()).thenReturn(List.of(inter));
        when(bookingAssignmentRepository.findByBookingID_Id(60)).thenReturn(List.of(
                BookingAssignment.builder().bookingID(inter).staffID(driver).build()));
        Instant sixDaysLater = inter.getDepartureDate().plusSeconds(6 * DAY);

        // Quốc tế: khoảng giữ [-2, +4] chồng nhau; nội địa [-1, +2] thì không
        assertThat(service.getAvailableCrew(sixDaysLater, "DRIVER", "international", null).getFirst().isAvailable()).isFalse();
        assertThatThrownBy(() -> service.validateCrewAvailability(1, sixDaysLater, "international", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("BK-INT00001");
        assertThat(service.getAvailableCrew(sixDaysLater, "DRIVER", "domestic", null).getFirst().isAvailable()).isTrue();
        service.validateCrewAvailability(1, sixDaysLater, "domestic", null);
    }

    private Vehicle vehicle(int id, String status) {
        return Vehicle.builder()
                .id(id)
                .vehicleType("Xe chuyên dụng 2 ngăn")
                .licensePlate("29H-12345")
                .status(status)
                .build();
    }

    private VehicleRequestDto request(int capacity, String status) {
        VehicleRequestDto request = new VehicleRequestDto();
        request.setType("Xe chuyên dụng");
        request.setCapacity(capacity);
        request.setPlate("29H-12345");
        request.setStatus(status);
        return request;
    }
}
