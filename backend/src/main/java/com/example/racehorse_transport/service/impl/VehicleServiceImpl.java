package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.vehicle.VehicleClassResponseDto;
import com.example.racehorse_transport.dto.vehicle.VehicleCreateRequestDto;
import com.example.racehorse_transport.dto.vehicle.VehicleResponseDto;
import com.example.racehorse_transport.dto.vehicle.VehicleStatusChangeRequestDto;
import com.example.racehorse_transport.dto.vehicle.VehicleUpdateRequestDto;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.VehicleClass;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.exception.VehicleRuleException;
import com.example.racehorse_transport.policy.VehiclePolicy;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.VehicleClassRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.AuditService;
import com.example.racehorse_transport.service.VehicleService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicles;
    private final VehicleClassRepository vehicleClasses;
    private final LocationRepository locations;
    private final VehicleTripRepository trips;
    private final AppUserRepository users;
    private final AuditService audit;

    @Override
    @Transactional(readOnly = true)
    public List<VehicleResponseDto> vehicles() {
        return vehicles.findAllByOrderByLicensePlateAsc().stream().map(VehicleResponseDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleResponseDto vehicle(Long id) {
        return vehicles.findOneById(id).map(VehicleResponseDto::from)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleClassResponseDto> vehicleClasses() {
        return vehicleClasses.findAll(Sort.by("code")).stream().map(VehicleClassResponseDto::from).toList();
    }

    @Override
    @Transactional
    public VehicleResponseDto createVehicle(VehicleCreateRequestDto request) {
        requireUniquePlate(request.normalizedLicensePlate(), null);
        VehicleClass vehicleClass = findClass(request.vehicleClassId());
        VehiclePolicy.requireCapacity(request.standardStallCount(), request.wideStallCount(), vehicleClass.getMaxStallCount());
        Vehicle vehicle = request.toEntity(vehicleClass, findHeadquarters(request.homeHubLocationId()));
        vehicle.setStatus(VehicleStatus.ACTIVE);
        return persist(vehicle);
    }

    @Override
    @Transactional
    public VehicleResponseDto updateVehicle(Long id, VehicleUpdateRequestDto request) {
        Vehicle vehicle = vehicles.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe " + id));
        requireUniquePlate(request.normalizedLicensePlate(), id);
        VehicleClass vehicleClass = findClass(request.vehicleClassId());
        VehiclePolicy.requireCapacity(request.standardStallCount(), request.wideStallCount(), vehicleClass.getMaxStallCount());
        request.applyTo(vehicle, vehicleClass, findHeadquarters(request.homeHubLocationId()));
        return persist(vehicle);
    }

    @Override
    @Transactional
    public VehicleResponseDto startMaintenance(Long id, String username, VehicleStatusChangeRequestDto request) {
        return changeStatus(id, username, request, VehicleStatus.MAINTENANCE, "START_MAINTENANCE");
    }

    @Override
    @Transactional
    public VehicleResponseDto reactivate(Long id, String username, VehicleStatusChangeRequestDto request) {
        return changeStatus(id, username, request, VehicleStatus.ACTIVE, "REACTIVATE");
    }

    @Override
    @Transactional
    public VehicleResponseDto retire(Long id, String username, VehicleStatusChangeRequestDto request) {
        return changeStatus(id, username, request, VehicleStatus.RETIRED, "RETIRE");
    }

    private VehicleResponseDto changeStatus(Long id, String username, VehicleStatusChangeRequestDto request,
                                            VehicleStatus next, String action) {
        var actor = users.findByUsername(username)
                .filter(user -> user.getStatus() == AppUserStatus.ACTIVE && "ADMIN".equals(user.getRole().getCode()))
                .orElseThrow(() -> new AccessDeniedException("Chỉ quản trị viên được đổi trạng thái xe."));
        Vehicle vehicle = vehicles.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe " + id));
        VehicleStatus previous = vehicle.getStatus();
        VehiclePolicy.requireStatusTransition(previous, next);
        if (next != VehicleStatus.ACTIVE && trips.existsByVehicle_IdAndStatusNotIn(id,
                Set.of(VehicleTripStatus.COMPLETED, VehicleTripStatus.CANCELLED))) {
            throw new VehicleRuleException("status", "Xe còn lượt chưa hoàn thành hoặc chưa hủy.", true);
        }
        vehicle.setStatus(next);
        audit.record(actor.getId(), action, "vehicle", id, Map.of("status", previous),
                Map.of("status", next, "reason", request.reason().strip()));
        return VehicleResponseDto.from(vehicle);
    }

    private VehicleClass findClass(Long id) {
        return vehicleClasses.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hạng xe " + id));
    }

    private Location findHeadquarters(Long id) {
        Location location = locations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa điểm " + id));
        VehiclePolicy.requireHeadquarters(location.getLocationType(), location.isHeadquarters());
        return location;
    }

    private void requireUniquePlate(String licensePlate, Long currentId) {
        if (vehicles.findByLicensePlateIgnoreCase(licensePlate).stream()
                .anyMatch(vehicle -> !vehicle.getId().equals(currentId))) {
            throw duplicatePlate();
        }
    }

    private VehicleResponseDto persist(Vehicle vehicle) {
        try {
            return VehicleResponseDto.from(vehicles.saveAndFlush(vehicle));
        } catch (DataIntegrityViolationException ex) {
            if (!(ex.getCause() instanceof ConstraintViolationException violation)
                    || !"uk_vehicle_license_plate".equals(violation.getConstraintName())) {
                throw ex;
            }
            throw duplicatePlate();
        }
    }

    private VehicleRuleException duplicatePlate() {
        return new VehicleRuleException("licensePlate", "Biển số xe đã được sử dụng.", true);
    }
}
