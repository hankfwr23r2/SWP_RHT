package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.vehicle.VehicleClassResponseDto;
import com.example.racehorse_transport.dto.vehicle.VehicleCreateRequestDto;
import com.example.racehorse_transport.dto.vehicle.VehicleResponseDto;
import com.example.racehorse_transport.dto.vehicle.VehicleStatusChangeRequestDto;
import com.example.racehorse_transport.dto.vehicle.VehicleUpdateRequestDto;
import java.util.List;

public interface VehicleService {

    List<VehicleResponseDto> vehicles();

    VehicleResponseDto vehicle(Long id);

    List<VehicleClassResponseDto> vehicleClasses();

    VehicleResponseDto createVehicle(VehicleCreateRequestDto request);

    VehicleResponseDto updateVehicle(Long id, VehicleUpdateRequestDto request);

    VehicleResponseDto startMaintenance(Long id, String username, VehicleStatusChangeRequestDto request);

    VehicleResponseDto reactivate(Long id, String username, VehicleStatusChangeRequestDto request);

    VehicleResponseDto retire(Long id, String username, VehicleStatusChangeRequestDto request);
}
