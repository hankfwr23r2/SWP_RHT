package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.dispatch.DispatchVehicleResponseDto;
import com.example.racehorse_transport.dto.vehicle.VehicleClassResponseDto;
import com.example.racehorse_transport.dto.vehicle.VehicleCreateRequestDto;
import com.example.racehorse_transport.dto.vehicle.VehicleResponseDto;
import com.example.racehorse_transport.dto.vehicle.VehicleStatusChangeRequestDto;
import com.example.racehorse_transport.dto.vehicle.VehicleUpdateRequestDto;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.VehicleClass;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.exception.VehicleRuleException;
import com.example.racehorse_transport.policy.DispatchPolicy;
import com.example.racehorse_transport.policy.VehiclePolicy;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.VehicleClassRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.AuditService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.VehicleService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final StaffRepository staff;
    private final NotificationService notifications;

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
        Vehicle vehicle = request.toEntity(vehicleClass, findHomeHub(request.homeHubLocationId()));
        vehicle.setStatus(VehicleStatus.ACTIVE);
        return persist(vehicle);
    }

    @Override
    @Transactional
    public VehicleResponseDto updateVehicle(Long id, VehicleUpdateRequestDto request) {
        Vehicle vehicle = lockVehicle(id);
        requireUniquePlate(request.normalizedLicensePlate(), id);
        VehicleClass vehicleClass = findClass(request.vehicleClassId());
        VehiclePolicy.requireCapacity(request.standardStallCount(), request.wideStallCount(), vehicleClass.getMaxStallCount());
        request.applyTo(vehicle, vehicleClass, findHomeHub(request.homeHubLocationId()));
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

    @Override
    @Transactional(readOnly = true)
    public List<DispatchVehicleResponseDto> vehiclesForDispatch() {
        return vehicles.findAllByOrderByLicensePlateAsc().stream().map(this::describeForDispatch).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DispatchVehicleResponseDto vehicleForDispatch(Long id) {
        return vehicles.findOneById(id).map(this::describeForDispatch)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe " + id));
    }

    private DispatchVehicleResponseDto describeForDispatch(Vehicle vehicle) {
        VehicleTrip last = trips.findByVehicle_IdAndStatusNot(vehicle.getId(), VehicleTripStatus.CANCELLED).stream()
                .max(Comparator.comparing(VehicleTrip::getPlannedEndAt)).orElse(null);
        return DispatchVehicleResponseDto.from(vehicle, DispatchPolicy.endLocationOrHome(vehicle, last),
                last == null ? null : last.getPlannedEndAt());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Vehicle lockVehicle(Long id) {
        return vehicles.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe " + id));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markForMaintenance(Vehicle vehicle, Long actorUserId, String reason, Instant reportedAt) {
        if (vehicle.getStatus() == VehicleStatus.MAINTENANCE) return;
        VehiclePolicy.requireStatusTransition(vehicle.getStatus(), VehicleStatus.MAINTENANCE);
        updateStatus(vehicle, VehicleStatus.MAINTENANCE, actorUserId, "START_MAINTENANCE", reason);
        List<String> upcoming = trips.findUpcomingCodes(vehicle.getId(), VehicleTripStatus.PLANNED, reportedAt);
        String message = "Xe " + vehicle.getLicensePlate() + " cần bảo dưỡng. " + reason;
        if (!upcoming.isEmpty()) {
            message += "\nCần bố trí xe khác cho các lượt đã lên lịch: " + String.join(", ", upcoming) + ".";
        }
        notifyVehicleMaintenance("COORDINATOR", message, "/coordinator/dispatch");
        notifyVehicleMaintenance("ADMIN", message, "/admin/vehicles");
    }

    private VehicleResponseDto changeStatus(Long id, String username, VehicleStatusChangeRequestDto request,
                                            VehicleStatus next, String action) {
        var actor = users.findByUsername(username)
                .filter(user -> user.getStatus() == AppUserStatus.ACTIVE && "ADMIN".equals(user.getRole().getCode()))
                .orElseThrow(() -> new AccessDeniedException("Chỉ quản trị viên được đổi trạng thái xe."));
        Vehicle vehicle = lockVehicle(id);
        VehicleStatus previous = vehicle.getStatus();
        VehiclePolicy.requireStatusTransition(previous, next);
        if (next != VehicleStatus.ACTIVE && trips.existsByVehicle_IdAndStatusNotIn(id,
                Set.of(VehicleTripStatus.COMPLETED, VehicleTripStatus.CANCELLED))) {
            throw new VehicleRuleException("status", "Xe còn lượt chưa hoàn thành hoặc chưa hủy.", true);
        }
        updateStatus(vehicle, next, actor.getId(), action, request.reason().strip());
        return VehicleResponseDto.from(vehicle);
    }

    private void updateStatus(Vehicle vehicle, VehicleStatus next, Long actorUserId, String action, String reason) {
        VehicleStatus previous = vehicle.getStatus();
        vehicle.setStatus(next);
        audit.record(actorUserId, action, "vehicle", vehicle.getId(), Map.of("status", previous),
                Map.of("status", next, "reason", reason));
    }

    private void notifyVehicleMaintenance(String role, String message, String link) {
        List<Long> recipients = staff.findUserIdsByRoleAndStatus(role, AppUserStatus.ACTIVE);
        if (!recipients.isEmpty()) {
            notifications.notify(recipients, null, "Xe cần bảo dưỡng", message, link);
        }
    }

    private VehicleClass findClass(Long id) {
        return vehicleClasses.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hạng xe " + id));
    }

    private Location findHomeHub(Long id) {
        Location location = locations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa điểm " + id));
        VehiclePolicy.requireHomeHub(location.getLocationType());
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
