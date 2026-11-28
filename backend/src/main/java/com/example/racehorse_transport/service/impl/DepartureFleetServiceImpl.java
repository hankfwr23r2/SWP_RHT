package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.util.Optional;
import com.example.racehorse_transport.service.AuditService;
import com.example.racehorse_transport.dto.departure.DepartureVehicleResponseDto;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.DepartureVehicle;
import com.example.racehorse_transport.entity.VehicleClass;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.DepartureAllotmentType;
import com.example.racehorse_transport.entity.enums.DepartureAvailability;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.DepartureFleetPolicy;
import com.example.racehorse_transport.policy.DepartureFleetPolicy.Stock;
import com.example.racehorse_transport.repository.DepartureVehicleRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.VehicleClassRepository;
import com.example.racehorse_transport.service.DepartureFleetService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DepartureFleetServiceImpl implements DepartureFleetService {
    private final DepartureVehicleRepository allotments;
    private final VehicleClassRepository classes;
    private final LocationRepository locations;
    private final StaffRepository staff;
    private final NotificationService notifications;
    private final SystemConfigService config;
    private final Clock clock;
    private final AuditService audit;

    @Override
    @Transactional(readOnly = true)
    public VehicleClass vehicleClass(Long id) {
        return classes.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hạng xe."));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleClass> vehicleClasses() {
        return classes.findAll();
    }

    @Override
    @Transactional
    public List<Stock> freeStock(Departure departure, boolean lockHub) {
        return freeStock(departure, lockHub, false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Stock> privateStock(Departure departure, int stalls, boolean lockHub) {
        return DepartureFleetPolicy.selectPrivateVehicle(stalls, freeStock(departure, lockHub, true));
    }

    @Override
    @Transactional
    public void reservePrivate(Departure departure, Booking booking, int stalls) {
        Stock stock = privateStock(departure, stalls, true)
                .orElseThrow(() -> BookingRuleException.conflict("Chuyến này không còn xe riêng"));
        DepartureVehicle allotment = new DepartureVehicle();
        allotment.setDeparture(departure);
        allotment.setVehicleClass(vehicleClass(stock.vehicleClassId()));
        allotment.setVehicleCount(1);
        allotment.setAllotmentType(DepartureAllotmentType.PRIVATE);
        allotment.setBooking(booking);
        allotment.setCreatedAt(clock.instant());
        allotments.save(allotment);
    }

    @Override
    @Transactional
    public void releasePrivate(Departure departure, Long bookingId) {
        locations.lockFleetHub(departure.getRoute().getOrigin().getId());
        allotments.findByBooking_Id(bookingId)
                .filter(allotment -> allotment.getDeparture().getId().equals(departure.getId())).ifPresent(allotment -> {
            allotments.delete(allotment);
            allotments.flush();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleClass privateVehicleClass(Long bookingId) {
        return allotments.findByBooking_Id(bookingId).map(DepartureVehicle::getVehicleClass)
                .orElseThrow(() -> BookingRuleException.conflict("Đơn chưa giữ xe riêng."));
    }

    private List<Stock> freeStock(Departure departure, boolean lockHub, boolean privateMode) {
        Long hubId = departure.getRoute().getOrigin().getId();
        if (lockHub) {
            locations.lockFleetHub(hubId);
        }
        int rest = config.getInt("LINEHAUL_REST_HOURS");
        Instant start = departure.getPlannedDepartureAt();
        Instant end = DepartureFleetPolicy.busyUntil(start, departure.getRoute().getEstimatedDurationMinutes(), rest);
        return allotments.findSpareStock(hubId, start, end, rest, privateMode).stream()
                .filter(row -> row.getVehicleCount() > 0)
                .map(row -> new Stock(row.getVehicleClassId(), row.getStalls(), row.getVehicleCount())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long sellableCapacity(Departure departure) {
        return departure.getStallCapacity() + DepartureFleetPolicy.spareCapacity(freeStock(departure, false));
    }

    @Override
    @Transactional(readOnly = true)
    public DepartureAvailability availability(Departure departure, long spareCapacity) {
        return DepartureFleetPolicy.availability(departure.getStallCapacity(), departure.getBookedStallCount(),
                spareCapacity, config.getInt("DEPARTURE_NEARLY_FULL_PERCENT"));
    }

    @Override
    @Transactional
    public void attach(Departure departure, VehicleClass vehicleClass, int count, DepartureAllotmentType type, AppUser actor) {
        DepartureVehicle allotment = new DepartureVehicle();
        allotment.setDeparture(departure);
        allotment.setVehicleClass(vehicleClass);
        allotment.setVehicleCount(count);
        allotment.setAllotmentType(type);
        allotment.setCreatedBy(actor);
        allotment.setCreatedAt(clock.instant());
        allotments.save(allotment);
    }

    @Override
    @Transactional
    public void remove(DepartureVehicle allotment, Long actorId, String reason) {
        locations.lockFleetHub(allotment.getDeparture().getRoute().getOrigin().getId());
        audit.record(actorId, "REMOVE_DEPARTURE_VEHICLE", "departure", allotment.getDeparture().getId(),
                java.util.Map.of("allotmentId", allotment.getId(), "vehicleCount", allotment.getVehicleCount()),
                java.util.Map.of("reason", reason.strip()));
        allotments.delete(allotment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartureVehicle> allotments(Long departureId) {
        return allotments.findAllotments(departureId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartureVehicleResponseDto> vehicles(Long departureId) {
        return allotments.findAllotments(departureId).stream().map(DepartureVehicleResponseDto::from).toList();
    }

    @Override
    @Transactional
    public void notifyReinforcement(Departure departure, int count) {
        var recipients = new HashSet<>(staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE));
        if (departure.getCoordinator() != null) {
            recipients.add(departure.getCoordinator().getUserId());
        }
        if (!recipients.isEmpty()) {
            notifications.notify(List.copyOf(recipients), null, "Chuyến đã thêm xe để sẵn",
                    "Chuyến " + departure.getId() + " đã thêm " + count + " xe để sẵn.", null);
        }
    }
}
