package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.fleet.VehicleRequest;
import com.example.racehorse_transport.dto.fleet.VehicleResponse;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingVehicle;
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

        VehicleResponse response = service.updateVehicle("1", request(4, "MAINTENANCE"));

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

        VehicleResponse response = service.updateVehicle("1", request(6, "AVAILABLE"));

        assertThat(response.getCapacity()).isEqualTo(6);
        assertThat(response.getStatus()).isEqualTo("MAINTENANCE");
        assertThat(vehicle.getVehicleType()).isEqualTo("Xe chuyên dụng 6 ngăn");
        verify(vehicleRepository).save(vehicle);
    }

    private Vehicle vehicle(int id, String status) {
        return Vehicle.builder()
                .id(id)
                .vehicleType("Xe chuyên dụng 2 ngăn")
                .licensePlate("29H-12345")
                .status(status)
                .build();
    }

    private VehicleRequest request(int capacity, String status) {
        VehicleRequest request = new VehicleRequest();
        request.setType("Xe chuyên dụng");
        request.setCapacity(capacity);
        request.setPlate("29H-12345");
        request.setStatus(status);
        return request;
    }
}
