package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.repository.VehicleClassRepository;
import com.example.racehorse_transport.dto.network.DepartureScheduleRequestDto;
import com.example.racehorse_transport.dto.network.DepartureScheduleResponseDto;
import com.example.racehorse_transport.dto.network.RouteRequestDto;
import com.example.racehorse_transport.dto.network.RouteResponseDto;
import com.example.racehorse_transport.dto.network.RouteStopRequestDto;
import com.example.racehorse_transport.entity.DepartureSchedule;
import com.example.racehorse_transport.entity.DepartureScheduleDay;
import com.example.racehorse_transport.entity.DepartureScheduleDayId;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.ScheduleStatus;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.RouteStopsPolicy;
import com.example.racehorse_transport.repository.DepartureScheduleDayRepository;
import com.example.racehorse_transport.repository.DepartureScheduleRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.RouteRepository;
import com.example.racehorse_transport.repository.RouteStopRepository;
import com.example.racehorse_transport.service.RouteService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RouteServiceImpl implements RouteService {

    private final RouteRepository routes;
    private final VehicleClassRepository vehicleClasses;
    private final SystemConfigService config;
    private final RouteStopRepository routeStops;
    private final LocationRepository locations;
    private final DepartureScheduleRepository schedules;
    private final DepartureScheduleDayRepository scheduleDays;

    @Override
    @Transactional(readOnly = true)
    public List<RouteResponseDto> routes() {
        return routes.findAllWithEndpoints().stream().map(route -> RouteResponseDto.from(route, null)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RouteResponseDto route(Long id) {
        return RouteResponseDto.from(findRoute(id), routeStops.findWithLocation(id));
    }

    @Override
    @Transactional
    public RouteResponseDto createRoute(RouteRequestDto request) {
        requireUniqueCode(request.getCode(), null);
        Location origin = activeLocation(request.getOriginLocationId());
        Location destination = activeLocation(request.getDestinationLocationId());
        Map<Long, Location> stopLocations = validateStops(request, origin, destination);

        Route route = request.toEntity(origin, destination);
        route.setStatus(ActiveStatus.ACTIVE);
        route = routes.save(route);
        saveStops(route, request.getStops(), stopLocations);
        return RouteResponseDto.from(route, routeStops.findWithLocation(route.getId()));
    }

    @Override
    @Transactional
    public RouteResponseDto updateRoute(Long id, RouteRequestDto request) {
        Route route = findRoute(id);
        requireUniqueCode(request.getCode(), id);
        Location origin = activeLocation(request.getOriginLocationId());
        Location destination = activeLocation(request.getDestinationLocationId());
        Map<Long, Location> stopLocations = validateStops(request, origin, destination);

        request.applyTo(route, origin, destination);
        // Thay toàn bộ điểm dừng; lệnh xóa tự flush thay đổi của tuyến trước khi chạy
        routeStops.deleteByRouteId(id);
        saveStops(route, request.getStops(), stopLocations);
        return RouteResponseDto.from(route, routeStops.findWithLocation(id));
    }

    @Override
    @Transactional
    public RouteResponseDto activateRoute(Long id) {
        Route route = findRoute(id);
        if (route.getOrigin().getStatus() != ActiveStatus.ACTIVE || route.getDestination().getStatus() != ActiveStatus.ACTIVE) {
            throw new IllegalStateException("Điểm đầu hoặc điểm cuối của tuyến đang ngừng dùng.");
        }
        route.setStatus(ActiveStatus.ACTIVE);
        return RouteResponseDto.from(route, null);
    }

    @Override
    @Transactional
    public RouteResponseDto deactivateRoute(Long id) {
        Route route = findRoute(id);
        route.setStatus(ActiveStatus.INACTIVE);
        return RouteResponseDto.from(route, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartureScheduleResponseDto> schedules(Long routeId) {
        Map<Long, List<DepartureScheduleDay>> daysBySchedule = scheduleDays.findAll().stream()
                .collect(Collectors.groupingBy(day -> day.getId().getDepartureScheduleId()));
        return schedules.findWithRoute(routeId).stream()
                .map(schedule -> DepartureScheduleResponseDto.from(schedule, daysBySchedule.getOrDefault(schedule.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public DepartureScheduleResponseDto createSchedule(DepartureScheduleRequestDto request) {
        Route route = activeRoute(request.getRouteId());
        requireValidPeriod(request);
        DepartureSchedule schedule = request.toEntity(route, vehicleClasses.findById(request.getBaseVehicleClassId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hạng xe.")));
        schedule.setStatus(ScheduleStatus.ACTIVE);
        schedule = schedules.save(schedule);
        return DepartureScheduleResponseDto.from(schedule, saveDays(schedule, request.getDaysOfWeek()));
    }

    @Override
    @Transactional
    public DepartureScheduleResponseDto updateSchedule(Long id, DepartureScheduleRequestDto request) {
        DepartureSchedule schedule = findSchedule(id);
        if (schedule.getStatus() == ScheduleStatus.ENDED) {
            throw new IllegalStateException("Lịch đã kết thúc, không sửa được.");
        }
        Route route = activeRoute(request.getRouteId());
        requireValidPeriod(request);
        request.applyTo(schedule, route, vehicleClasses.findById(request.getBaseVehicleClassId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hạng xe.")));
        // Đổi lịch không đổi các chuyến đã sinh: sức chứa và giờ chạy đã chép vào từng chuyến
        scheduleDays.deleteByScheduleId(id);
        return DepartureScheduleResponseDto.from(schedule, saveDays(schedule, request.getDaysOfWeek()));
    }

    @Override
    @Transactional
    public DepartureScheduleResponseDto pauseSchedule(Long id) {
        return changeScheduleStatus(id, Set.of(ScheduleStatus.ACTIVE), ScheduleStatus.PAUSED);
    }

    @Override
    @Transactional
    public DepartureScheduleResponseDto resumeSchedule(Long id) {
        return changeScheduleStatus(id, Set.of(ScheduleStatus.PAUSED), ScheduleStatus.ACTIVE);
    }

    @Override
    @Transactional
    public DepartureScheduleResponseDto endSchedule(Long id) {
        return changeScheduleStatus(id, Set.of(ScheduleStatus.ACTIVE, ScheduleStatus.PAUSED), ScheduleStatus.ENDED);
    }

    private DepartureScheduleResponseDto changeScheduleStatus(Long id, Set<ScheduleStatus> allowedFrom, ScheduleStatus to) {
        DepartureSchedule schedule = findSchedule(id);
        if (!allowedFrom.contains(schedule.getStatus())) {
            throw new IllegalStateException("Không chuyển được lịch từ " + schedule.getStatus() + " sang " + to + ".");
        }
        schedule.setStatus(to);
        return DepartureScheduleResponseDto.from(schedule, scheduleDays.findById_DepartureScheduleId(id));
    }

    // Kiểm điểm dừng bằng RouteStopsPolicy; trả về địa điểm của các điểm dừng theo id
    private Map<Long, Location> validateStops(RouteRequestDto request, Location origin, Location destination) {
        List<Long> ids = request.getStops().stream().map(RouteStopRequestDto::getLocationId).distinct().toList();
        Map<Long, Location> byId = locations.findAllById(ids).stream().collect(Collectors.toMap(Location::getId, Function.identity()));
        for (Long locationId : ids) {
            Location location = byId.get(locationId);
            if (location == null) {
                throw new IllegalArgumentException("Không tìm thấy địa điểm " + locationId + " của điểm dừng.");
            }
            if (location.getStatus() != ActiveStatus.ACTIVE) {
                throw new IllegalArgumentException("Địa điểm " + location.getCode() + " đang ngừng dùng.");
            }
        }
        List<RouteStopsPolicy.Stop> stops = request.getStops().stream()
                .map(stop -> new RouteStopsPolicy.Stop(stop.getSequenceNumber(), stop.getLocationId(), byId.get(stop.getLocationId()).getLocationType(),
                        stop.getStopType(), stop.getArrivalOffsetMinutes(), stop.getDwellMinutes()))
                .toList();
        var limits = new RouteStopsPolicy.Limits(config.getInt("MAX_CONTINUOUS_HOURS"),
                config.getInt("MIN_REST_MINUTES"), config.getInt("MAX_DRIVE_HOURS_PER_DAY"),
                config.getInt("OVERNIGHT_REST_HOURS"));
        List<String> errors = RouteStopsPolicy.violations(origin.getId(), origin.getLocationType(),
                destination.getId(), destination.getLocationType(), stops, limits);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", errors));
        }
        return byId;
    }

    private void saveStops(Route route, List<RouteStopRequestDto> stops, Map<Long, Location> stopLocations) {
        routeStops.saveAll(stops.stream().map(stop -> stop.toEntity(route, stopLocations.get(stop.getLocationId()))).toList());
        routeStops.flush();
    }

    private List<DepartureScheduleDay> saveDays(DepartureSchedule schedule, Set<Short> daysOfWeek) {
        List<DepartureScheduleDay> days = daysOfWeek.stream().sorted().map(dayOfWeek -> {
            DepartureScheduleDay day = new DepartureScheduleDay();
            day.setId(new DepartureScheduleDayId(schedule.getId(), dayOfWeek));
            day.setDepartureSchedule(schedule);
            return day;
        }).toList();
        return scheduleDays.saveAll(days);
    }

    private void requireValidPeriod(DepartureScheduleRequestDto request) {
        if (request.getValidTo() != null && request.getValidTo().isBefore(request.getValidFrom())) {
            throw new IllegalArgumentException("Ngày hết hiệu lực phải sau hoặc bằng ngày bắt đầu.");
        }
    }

    private void requireUniqueCode(String code, Long selfId) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        routes.findByCode(normalized)
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new IllegalStateException("Mã tuyến " + normalized + " đã được dùng.");
                });
    }

    private Route findRoute(Long id) {
        return routes.findWithEndpoints(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tuyến " + id));
    }

    private Route activeRoute(Long id) {
        Route route = findRoute(id);
        if (route.getStatus() != ActiveStatus.ACTIVE) {
            throw new IllegalArgumentException("Tuyến " + route.getCode() + " đang ngừng chạy.");
        }
        return route;
    }

    private Location activeLocation(Long id) {
        Location location = locations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa điểm " + id));
        if (location.getStatus() != ActiveStatus.ACTIVE) {
            throw new IllegalArgumentException("Địa điểm " + location.getCode() + " đang ngừng dùng.");
        }
        return location;
    }

    private DepartureSchedule findSchedule(Long id) {
        return schedules.findWithRouteById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch chạy " + id));
    }
}
