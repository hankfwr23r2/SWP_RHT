package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.fleet.CrewResponseDto;
import com.example.racehorse_transport.dto.fleet.VehicleResponseDto;
import com.example.racehorse_transport.dto.fleet.VehicleRequestDto;

import java.time.Instant;
import java.util.List;

public interface FleetService {
    List<VehicleResponseDto> getAllVehicles();
    /** Xe chọn được cho một ngày đi; bookingRef (mã hoặc ID đơn) là đơn đang xếp, không tự khóa chính nó. */
    List<VehicleResponseDto> getAvailableVehicles(Instant departDate, String tripType, String bookingRef);
    VehicleResponseDto createVehicle(VehicleRequestDto request);
    VehicleResponseDto updateVehicle(String identifier, VehicleRequestDto request);
    void deleteVehicle(String identifier);
    List<CrewResponseDto> getAllCrew(String role);
    /** Cùng quy tắc với validateCrewAvailability để danh sách và lúc lưu không lệch nhau. */
    List<CrewResponseDto> getAvailableCrew(Instant departDate, String role, String tripType, String bookingRef);
    com.example.racehorse_transport.entity.Vehicle findVehicleByIdOrCode(String identifier);
    void validateVehicleAvailability(Integer vehicleId, Instant departDate, String tripType, Integer excludeBookingId);
    com.example.racehorse_transport.entity.Staff findStaffByIdOrCode(String identifier, String expectedRole);
    void validateCrewAvailability(Integer staffId, Instant departDate, String tripType, Integer excludeBookingId);
}
