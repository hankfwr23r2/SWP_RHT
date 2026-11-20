package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayloadDto;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.dto.fleet.CrewResponseDto;
import com.example.racehorse_transport.dto.fleet.VehicleRequestDto;
import com.example.racehorse_transport.dto.fleet.VehicleResponseDto;
import com.example.racehorse_transport.dto.fleet.VehicleScheduleDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingAssignment;
import com.example.racehorse_transport.entity.BookingVehicle;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.repository.BookingAssignmentRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingVehicleRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.service.FleetService;
import com.example.racehorse_transport.util.BookingDetailsJson;
import com.example.racehorse_transport.util.BookingSchedulePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Quy tắc giữ lịch xe và kíp (một quy tắc cho danh sách chọn và lúc lưu):
 * <ol>
 *   <li>Mỗi đơn đã gán tài nguyên giữ nó trong khoảng quanh ngày đi D: nội địa [D-1, D+2], quốc tế [D-2, D+4].</li>
 *   <li>Khoảng giữ không đổi dù đơn chưa chạy hay đang chạy.</li>
 *   <li>Đã rời trụ sở mà chưa xác nhận về: khoảng giữ kéo tới giờ về dự kiến, và không bao giờ kết thúc trước
 *       "hiện tại + {@link #NOT_BACK_BUFFER}" cho tới khi xác nhận về (Single HQ).</li>
 *   <li>Đơn mới chỉ bị khóa khi khoảng giữ của nó chồng lên một khoảng giữ khác. Có đơn ở ngày khác vẫn nhận được.</li>
 * </ol>
 * Rủi ro về trễ không bị chặn trước; về trễ thật thì cờ "Về trễ" báo điều phối xử lý.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FleetServiceImpl implements FleetService {

    private final VehicleRepository vehicleRepository;
    private final StaffRepository staffRepository;
    private final BookingRepository bookingRepository;
    private final BookingVehicleRepository bookingVehicleRepository;
    private final BookingAssignmentRepository bookingAssignmentRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(BookingSchedulePolicy.VIETNAM);
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
            .withZone(BookingSchedulePolicy.VIETNAM);

    // Tài nguyên chưa xác nhận về trụ sở thì không nhận đơn đi trước "hiện tại + khoảng này"
    private static final Duration NOT_BACK_BUFFER = Duration.ofDays(1);

    // Danh sách các trạng thái đơn hàng đang chiếm dụng tài nguyên xe và nhân sự
    private static final Set<String> HOLDING_STATUSES = Set.of(
            "under_review",
            "pending_commercial",
            "awaiting_payment",
            "waybill_issued",
            "clearance_in_progress",
            "clearance_done",
            "ready_for_pickup",
            "en_route_to_pickup",
            "pickup_failed",
            "returning_horses",
            "in_transit",
            "incident_reported",
            "pending_emergency_approval",
            "emergency_plan_active");

    // Đơn đang thực tế lăn bánh ngoài đường: tài nguyên đã rời trụ sở
    private static final Set<String> ACTIVE_ROAD_STATUSES = Set.of(
            "en_route_to_pickup", "pickup_failed", "returning_horses", "in_transit", "incident_reported",
            "pending_emergency_approval", "emergency_plan_active", "arrived", "handover_timeout",
            "returning_to_hq", "delivered_pending_settlement");

    /** Khoảng thời gian giữ tài nguyên. */
    private record Window(Instant start, Instant end) {
        boolean overlaps(Window other) {
            return !end.isBefore(other.start) && !start.isAfter(other.end);
        }
    }

    /**
     * Một khoảng giữ tài nguyên của một đơn.
     * away = tài nguyên đã rời trụ sở, chưa xác nhận về; returning = đã giao, đang về; overdue = đã quá giờ dự kiến.
     */
    private record Hold(Booking booking, Window window, boolean away, boolean returning, Long eta, boolean overdue) { }

    private static boolean isInternational(String tripType) {
        return "international".equalsIgnoreCase(tripType);
    }

    private static Window holdWindow(Instant depart, String tripType) {
        boolean inter = isInternational(tripType);
        return new Window(depart.minus(inter ? 2 : 1, ChronoUnit.DAYS), depart.plus(inter ? 4 : 2, ChronoUnit.DAYS));
    }

    private static Instant departOf(Booking booking) {
        return booking.getDepartureDate() != null ? booking.getDepartureDate() : booking.getBookingDate();
    }

    private static Window holdWindow(Booking booking) {
        Instant depart = departOf(booking);
        return depart == null ? null : holdWindow(depart, booking.getBookingType());
    }

    private static String tripTag(Booking booking) {
        return isInternational(booking.getBookingType()) ? "quốc tế" : "nội địa";
    }

    private static String statusOf(Booking booking) {
        return booking.getStatus() == null ? "" : booking.getStatus().toLowerCase(Locale.ROOT);
    }

    private static Hold plannedHold(Booking booking, Window window) {
        return new Hold(booking, window, false, false, null, false);
    }

    // Đã rời trụ sở, chưa xác nhận về: theo kế hoạch (hoặc giờ về dự kiến nếu muộn hơn),
    // không kết thúc trước "hiện tại + NOT_BACK_BUFFER" cho tới khi xác nhận về.
    private static Hold notBackHold(Booking booking, boolean returning, Long eta, Instant now) {
        Window planned = holdWindow(booking);
        Instant start = planned != null && planned.start().isBefore(now) ? planned.start() : now;
        // Mốc về dự kiến: muộn nhất giữa cuối khoảng kế hoạch và giờ về dự kiến (nếu có)
        Instant expected = planned != null ? planned.end() : null;
        if (eta != null && (expected == null || Instant.ofEpochMilli(eta).isAfter(expected))) {
            expected = Instant.ofEpochMilli(eta);
        }
        boolean overdue = expected != null && now.isAfter(expected);
        Instant floor = now.plus(NOT_BACK_BUFFER);
        Instant end = expected == null || floor.isAfter(expected) ? floor : expected;
        return new Hold(booking, new Window(start, end), true, returning, eta, overdue);
    }

    // Câu lý do dùng chung cho thẻ chọn và lỗi khi lưu, để người dùng chỉ thấy một cách diễn đạt
    private static String holdText(Hold hold) {
        Booking b = hold.booking();
        if (!hold.away()) {
            return "Trùng lịch với đơn " + b.getCode() + " (" + tripTag(b) + ", khởi hành "
                    + DAY.format(departOf(b)) + ", bận tới " + DAY.format(hold.window().end()) + ")";
        }
        String what = hold.returning()
                ? "Đang về trụ sở từ đơn " + b.getCode()
                        + (hold.eta() != null ? " (dự kiến " + DAY_TIME.format(Instant.ofEpochMilli(hold.eta())) + ")" : "")
                : "Đang đi đơn " + b.getCode() + " (" + tripTag(b) + ")";
        return what + (hold.overdue() ? ", đã quá giờ dự kiến về" : "")
                + ", bận tới " + DAY.format(hold.window().end());
    }

    private static Hold firstConflict(List<Hold> holds, Window wanted) {
        if (holds == null) return null;
        return holds.stream()
                .filter(h -> h.window().overlaps(wanted))
                .min(Comparator.comparing((Hold h) -> h.window().start()))
                .orElse(null);
    }

    private static Hold awayOf(List<Hold> holds) {
        return holds == null ? null : holds.stream().filter(Hold::away).findFirst().orElse(null);
    }

    private boolean isBusyStatus(String status) {
        if (status == null || status.isBlank()) return false;
        String s = status.toUpperCase(Locale.ROOT).replace("_", "").replace(" ", "").replace("-", "");
        return "BUSY".equals(s) || "INUSE".equals(s) || "ONTRIP".equals(s);
    }

    private boolean isMaintenanceStatus(String status) {
        if (status == null || status.isBlank()) return false;
        String s = status.toUpperCase(Locale.ROOT).replace("_", "").replace(" ", "").replace("-", "");
        return "MAINTENANCE".equals(s) || "UNAVAILABLE".equals(s);
    }

    // Không truyền ngày: trạng thái hiện tại của từng xe cho trang Đội xe.
    // available = sẵn sàng hẳn (ở trụ sở, chưa nhận đơn nào); upcoming liệt kê đơn đã nhận chưa lăn bánh.
    @Override
    public List<VehicleResponseDto> getAllVehicles() {
        Map<Integer, List<Hold>> holds = vehicleHolds(null);

        return vehicleRepository.findAll().stream().map(v -> {
            VehicleResponseDto res = mapToVehicleResponse(v);
            List<Hold> held = holds.get(v.getId());
            List<VehicleScheduleDto> upcoming = upcomingOf(held);
            res.setUpcoming(upcoming);
            Hold away = awayOf(held);
            applyAway(res, away);
            if (isMaintenanceStatus(v.getStatus())) {
                res.setAvailable(false);
                res.setBusyReason("Xe đang bảo dưỡng định kỳ");
            } else if (away != null) {
                res.setAvailable(false);
                res.setBusyReason(holdText(away));
                res.setBusyBookingCode(away.booking().getCode());
                res.setBusyUntil(away.window().end().toEpochMilli());
            } else if (isBusyStatus(v.getStatus())) {
                res.setAvailable(false);
                res.setBusyReason("Xe đang được gán cho chuyến đi");
            } else if (!upcoming.isEmpty()) {
                VehicleScheduleDto next = upcoming.getFirst();
                res.setAvailable(false);
                res.setBusyReason("Đã nhận đơn " + next.getBookingCode() + " (khởi hành "
                        + DAY.format(Instant.ofEpochMilli(next.getDepartAt())) + ")");
                res.setBusyBookingCode(next.getBookingCode());
                res.setBusyUntil(next.getBusyUntil());
            } else {
                res.setAvailable(true);
            }
            return res;
        }).toList();
    }

    // Có ngày đi: xe nào chọn được cho đơn đó. Đơn đang xếp (bookingRef) không tự khóa xe của chính nó.
    @Override
    public List<VehicleResponseDto> getAvailableVehicles(Instant departDate, String tripType, String bookingRef) {
        Integer excludeBookingId = resolveBookingId(bookingRef);
        Window wanted = holdWindow(departDate != null ? departDate : Instant.now(), tripType);
        Map<Integer, List<Hold>> holds = vehicleHolds(excludeBookingId);

        return vehicleRepository.findAll().stream().map(v -> {
            VehicleResponseDto res = mapToVehicleResponse(v);
            List<Hold> held = holds.get(v.getId());
            res.setUpcoming(upcomingOf(held));
            applyAway(res, awayOf(held)); // chỉ để hiển thị "đang đi đơn / đang về"
            Hold conflict = firstConflict(held, wanted);
            if (isMaintenanceStatus(v.getStatus())) {
                res.setAvailable(false);
                res.setBusyReason("Xe đang bảo dưỡng định kỳ");
            } else if (isBusyStatus(v.getStatus())) {
                res.setAvailable(false);
                res.setBusyReason("Xe đang được gán cho chuyến đi");
            } else if (conflict != null) {
                res.setAvailable(false);
                res.setBusyReason(holdText(conflict));
                res.setBusyBookingCode(conflict.booking().getCode());
                res.setBusyUntil(conflict.window().end().toEpochMilli());
            } else {
                res.setAvailable(true);
            }
            return res;
        }).toList();
    }

    private void applyAway(VehicleResponseDto res, Hold away) {
        if (away == null) return;
        res.setOnTrip(true);
        res.setOnTripBookingCode(away.booking().getCode());
        res.setReturnEta(away.returning() ? away.eta() : null);
    }

    // Đơn đã nhận xe nhưng chưa lăn bánh, theo ngày đi tăng dần
    private List<VehicleScheduleDto> upcomingOf(List<Hold> holds) {
        if (holds == null) return new ArrayList<>();
        return holds.stream()
                .filter(h -> !h.away() && !ACTIVE_ROAD_STATUSES.contains(statusOf(h.booking())))
                .sorted(Comparator.comparing((Hold h) -> h.window().start()))
                .map(h -> VehicleScheduleDto.builder()
                        .bookingCode(h.booking().getCode())
                        .tripType(isInternational(h.booking().getBookingType()) ? "international" : "domestic")
                        .departAt(departOf(h.booking()).toEpochMilli())
                        .busyFrom(h.window().start().toEpochMilli())
                        .busyUntil(h.window().end().toEpochMilli())
                        .build())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    // VehicleID -> các khoảng giữ xe
    private Map<Integer, List<Hold>> vehicleHolds(Integer excludeBookingId) {
        Instant now = Instant.now();
        Map<Integer, List<Hold>> holds = new HashMap<>();
        for (Booking booking : bookingRepository.findAll()) {
            if (Objects.equals(booking.getId(), excludeBookingId)) continue;
            String status = statusOf(booking);
            boolean onRoad = ACTIVE_ROAD_STATUSES.contains(status);

            // Xe của đơn đã rời trụ sở và chưa xác nhận về (kể cả đơn đã đóng tài chính)
            Set<Integer> notBack = new HashSet<>();
            BookingDetailsPayloadDto payload = BookingDetailsJson.read(objectMapper, booking);
            if (payload.getTrips() != null) {
                for (VehicleTripDto trip : payload.getTrips()) {
                    if (trip.getVehicleId() == null) continue;
                    Map<String, Object> hr = trip.getHomeReturn();
                    boolean returning = hr != null;
                    if (returning && hr.get("backAt") != null) continue; // chuyến này đã về trụ sở
                    if (!returning && !onRoad) continue; // chưa lăn bánh: giữ theo kế hoạch bên dưới
                    Integer vehicleId = resolveTripVehicleId(trip.getVehicleId());
                    if (vehicleId == null || !notBack.add(vehicleId)) continue;
                    holds.computeIfAbsent(vehicleId, k -> new ArrayList<>())
                            .add(notBackHold(booking, returning, returning ? epochOf(hr.get("eta")) : null, now));
                }
            }

            // Xe được gán cho đơn còn giữ xe: giữ theo khoảng kế hoạch
            Window window = HOLDING_STATUSES.contains(status) ? holdWindow(booking) : null;
            if (window == null) continue;
            for (BookingVehicle bv : bookingVehicleRepository.findByBookingID_Id(booking.getId())) {
                if (bv.getVehicleID() == null || bv.getVehicleID().getId() == null) continue;
                Integer vehicleId = bv.getVehicleID().getId();
                if (notBack.contains(vehicleId)) continue;
                holds.computeIfAbsent(vehicleId, k -> new ArrayList<>()).add(plannedHold(booking, window));
            }
        }
        return holds;
    }

    // StaffID -> các khoảng giữ nhân sự, cùng quy tắc với xe
    private Map<Integer, List<Hold>> crewHolds(List<Staff> staffList, Integer excludeBookingId) {
        Instant now = Instant.now();
        Map<Integer, List<Hold>> holds = new HashMap<>();
        for (Booking booking : bookingRepository.findAll()) {
            if (Objects.equals(booking.getId(), excludeBookingId)) continue;
            String status = statusOf(booking);
            boolean onRoad = ACTIVE_ROAD_STATUSES.contains(status);
            Window window = HOLDING_STATUSES.contains(status) ? holdWindow(booking) : null;
            BookingDetailsPayloadDto payload = BookingDetailsJson.read(objectMapper, booking);
            List<BookingAssignment> assignments = null;

            for (Staff staff : staffList) {
                VehicleTripDto trip = notBackTripOf(payload, staff, onRoad);
                if (trip != null) {
                    Map<String, Object> hr = trip.getHomeReturn();
                    holds.computeIfAbsent(staff.getId(), k -> new ArrayList<>())
                            .add(notBackHold(booking, hr != null, hr != null ? epochOf(hr.get("eta")) : null, now));
                    continue;
                }
                if (!onRoad && window == null) continue;
                if (assignments == null) assignments = bookingAssignmentRepository.findByBookingID_Id(booking.getId());
                if (!isStaffAssignedToBooking(staff, assignments, payload)) continue;
                holds.computeIfAbsent(staff.getId(), k -> new ArrayList<>())
                        .add(onRoad ? notBackHold(booking, false, null, now) : plannedHold(booking, window));
            }
        }
        return holds;
    }

    private Integer resolveTripVehicleId(String rawIdentifier) {
        String identifier = rawIdentifier.trim();
        if (identifier.matches("(?i)(?:VH-|XE-)?[0-9]+")) {
            try {
                return Integer.valueOf(identifier.replaceFirst("(?i)^(VH|XE)-", ""));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return vehicleRepository.findByLicensePlate(identifier).map(Vehicle::getId).orElse(null);
    }

    private static Long epochOf(Object value) {
        return value instanceof Number n && n.longValue() > 0 ? n.longValue() : null;
    }

    // Nhận mã đơn (BK-...) hoặc ID số; không tìm thấy thì không loại trừ đơn nào
    private Integer resolveBookingId(String bookingRef) {
        if (bookingRef == null || bookingRef.isBlank()) return null;
        String ref = bookingRef.trim();
        if (ref.matches("\\d+")) {
            try {
                return Integer.valueOf(ref);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return bookingRepository.findIdByCode(ref).orElse(null);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public VehicleResponseDto createVehicle(VehicleRequestDto request) {
        String plate = request.getPlate().trim().toUpperCase(Locale.ROOT);
        if (vehicleRepository.findByLicensePlate(plate).isPresent()) {
            throw new IllegalArgumentException("Biển số xe đã tồn tại.");
        }
        Vehicle vehicle = request.toEntity(plate);
        return mapToVehicleResponse(vehicleRepository.save(vehicle));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public VehicleResponseDto updateVehicle(String identifier, VehicleRequestDto request) {
        Vehicle vehicle = findVehicleByIdOrCode(identifier);
        boolean isBusy = isBusyStatus(vehicle.getStatus())
                || bookingVehicleRepository.findByVehicleID_Id(vehicle.getId()).stream()
                        .anyMatch(bv -> bv.getBookingID() != null && bv.getBookingID().getStatus() != null
                                && HOLDING_STATUSES.contains(bv.getBookingID().getStatus().toLowerCase(Locale.ROOT)));
        if (isBusy) {
            throw new IllegalStateException("Xe đang bận hoặc đang được giữ cho chuyến đi, không thể chỉnh sửa.");
        }

        if (request.getCapacity() != null && request.getCapacity() > 0) {
            vehicle.setCapacity(request.getCapacity());
        }

        return mapToVehicleResponse(vehicleRepository.save(vehicle));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void deleteVehicle(String identifier) {
        Vehicle vehicle = findVehicleByIdOrCode(identifier);
        boolean isBusy = "BUSY".equalsIgnoreCase(vehicle.getStatus())
                || "IN_USE".equalsIgnoreCase(vehicle.getStatus())
                || "ON_TRIP".equalsIgnoreCase(vehicle.getStatus())
                || !bookingVehicleRepository.findByVehicleID_Id(vehicle.getId()).isEmpty();
        if (isBusy) {
            throw new IllegalStateException("Không thể xóa xe đang bận hoặc đã được gán vào đơn hàng.");
        }
        vehicleRepository.delete(vehicle);
    }


    @Override
    public List<CrewResponseDto> getAllCrew(String role) {
        List<Staff> staffList = role != null && !role.isBlank() && !role.equalsIgnoreCase("ALL")
                ? staffRepository.findByRoleIgnoreCase(role)
                : staffRepository.findAll();
        return staffList.stream().map(this::mapToCrewResponse).toList();
    }

    @Override
    public List<CrewResponseDto> getAvailableCrew(Instant departDate, String role, String tripType, String bookingRef) {
        Integer excludeBookingId = resolveBookingId(bookingRef);
        Window wanted = holdWindow(departDate != null ? departDate : Instant.now(), tripType);

        List<Staff> staffList = role != null && !role.isBlank() && !role.equalsIgnoreCase("ALL")
                ? staffRepository.findByRoleIgnoreCase(role)
                : staffRepository.findAll();
        Map<Integer, List<Hold>> holds = crewHolds(staffList, excludeBookingId);

        return staffList.stream().map(s -> {
            CrewResponseDto res = mapToCrewResponse(s);
            Hold conflict = firstConflict(holds.get(s.getId()), wanted);
            if (!"ACTIVE".equalsIgnoreCase(s.getEmploymentStatus())) {
                res.setAvailable(false);
                res.setBusyReason("Tài khoản nhân sự đang tạm khóa / nghỉ phép");
            } else if (conflict != null) {
                res.setAvailable(false);
                res.setBusyReason(holdText(conflict));
                res.setBusyBookingCode(conflict.booking().getCode());
                res.setBusyUntil(conflict.window().end().toEpochMilli());
            } else {
                res.setAvailable(true);
                res.setBusyReason(null);
            }
            return res;
        }).toList();
    }

    private VehicleResponseDto mapToVehicleResponse(Vehicle v) {
        return VehicleResponseDto.builder()
                .id(v.getId())
                .code(v.getVehicleCode() != null ? v.getVehicleCode() : "VH-" + String.format("%03d", v.getId()))
                .name(v.getVehicleName() != null ? v.getVehicleName() : "Xe chuyên dụng " + v.getCapacity() + " ngăn")
                .type(v.getVehicleType() != null ? v.getVehicleType() : "medium")
                .plate(v.getLicensePlate())
                .capacity(v.getCapacity())
                .status(v.getStatus())
                .homeDepot("Trụ sở chính — Việt Nam")
                .currentLocation("Trụ sở chính — Việt Nam")
                .isAvailable(!"MAINTENANCE".equalsIgnoreCase(v.getStatus()))
                .busyReason(null)
                .build();
    }

    private CrewResponseDto mapToCrewResponse(Staff s) {
        String fullName = s.getUser() != null ? s.getUser().getUsername() : "Nhân viên " + s.getId();
        String email = s.getUser() != null ? s.getUser().getEmail() : "";
        return CrewResponseDto.builder()
                .userId(s.getId())
                .staffCode(s.getStaffCode() != null ? s.getStaffCode() : fallbackStaffCode(s))
                .name(fullName)
                .role(s.getRole())
                .phone(s.getPhone())
                .email(email)
                .employmentStatus(s.getEmploymentStatus())
                .isAvailable("ACTIVE".equalsIgnoreCase(s.getEmploymentStatus()))
                .busyReason(null)
                .build();
    }

    private String fallbackStaffCode(Staff staff) {
        String prefix = switch (staff.getRole() == null ? "" : staff.getRole().toUpperCase(Locale.ROOT)) {
            case "ADMIN" -> "ADMIN";
            case "DRIVER" -> "DR";
            case "ESCORT" -> "ES";
            default -> "NV";
        };
        return prefix + "-" + String.format("%02d", staff.getId());
    }

    @Override
    public Vehicle findVehicleByIdOrCode(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã xe không được để trống!");
        }
        String clean = identifier.trim();
        try {
            int numId = Integer.parseInt(clean);
            Optional<Vehicle> byId = vehicleRepository.findById(numId);
            if (byId.isPresent())
                return byId.get();
        } catch (NumberFormatException ignored) {
        }

        if (clean.matches("(?i)(VH|XE)-\\d+")) {
            String digits = clean.replaceAll("[^0-9]", "");
            try {
                int numId = Integer.parseInt(digits);
                Optional<Vehicle> byId = vehicleRepository.findById(numId);
                if (byId.isPresent())
                    return byId.get();
            } catch (NumberFormatException ignored) {
            }
        }

        Optional<Vehicle> byPlate = vehicleRepository.findByLicensePlate(clean);
        if (byPlate.isPresent())
            return byPlate.get();

        // Tìm kiếm dự phòng theo mã định danh sinh tự động (getVehicleCode)
        for (Vehicle v : vehicleRepository.findAll()) {
            if (clean.equalsIgnoreCase(v.getVehicleCode())) {
                return v;
            }
        }

        throw new IllegalArgumentException("Không tìm thấy xe phù hợp: " + identifier);
    }

    @Override
    public void validateVehicleAvailability(Integer vehicleId, Instant departDate, String tripType,
            Integer excludeBookingId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy xe có ID: " + vehicleId));

        if (isMaintenanceStatus(vehicle.getStatus())) {
            throw new IllegalStateException(
                    "Xe " + vehicle.getLicensePlate() + " đang bảo dưỡng định kỳ, không thể xếp xe!");
        }
        if (isBusyStatus(vehicle.getStatus())) {
            throw new IllegalStateException(
                    "Xe " + vehicle.getLicensePlate() + " (" + vehicle.getVehicleName() + ") đang bận chuyến đi khác!");
        }

        Window wanted = holdWindow(departDate != null ? departDate : Instant.now(), tripType);
        Hold conflict = firstConflict(vehicleHolds(excludeBookingId).get(vehicleId), wanted);
        if (conflict != null) {
            throw new IllegalStateException("Xe " + vehicle.getLicensePlate() + " không nhận được đơn này: "
                    + holdText(conflict) + ".");
        }
    }

    @Override
    public Staff findStaffByIdOrCode(String identifier, String expectedRole) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã nhân sự không được để trống!");
        }
        String clean = identifier.trim();
        Optional<Staff> staffOpt = Optional.empty();

        try {
            int numId = Integer.parseInt(clean);
            staffOpt = staffRepository.findById(numId);
        } catch (NumberFormatException ignored) {
        }

        if (staffOpt.isEmpty()) {
            staffOpt = staffRepository.findByStaffCode(clean);
        }

        if (staffOpt.isEmpty() && clean.matches("(?i)(TX|NV|DRV|ESC)-\\d+")) {
            String digits = clean.replaceAll("[^0-9]", "");
            try {
                int numId = Integer.parseInt(digits);
                staffOpt = staffRepository.findById(numId);
            } catch (NumberFormatException ignored) {
            }
        }

        if (staffOpt.isEmpty()) {
            Optional<User> userOpt = userRepository.findByUsernameOrEmail(clean, clean);
            if (userOpt.isPresent()) {
                staffOpt = staffRepository.findById(userOpt.get().getId());
            }
        }

        if (staffOpt.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy nhân sự phù hợp: " + identifier);
        }

        Staff staff = staffOpt.get();
        if (expectedRole != null && !expectedRole.trim().isEmpty() && !expectedRole.equalsIgnoreCase("ALL")) {
            if (staff.getRole() == null || !staff.getRole().equalsIgnoreCase(expectedRole)) {
                throw new IllegalArgumentException("Nhân sự " + staff.getStaffCode() + " không có vai trò "
                        + expectedRole + " (vai trò hiện tại: " + staff.getRole() + ")!");
            }
        }

        return staff;
    }

    @Override
    public void validateCrewAvailability(Integer staffId, Instant departDate, String tripType,
            Integer excludeBookingId) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân sự có ID: " + staffId));

        if (!"ACTIVE".equalsIgnoreCase(staff.getEmploymentStatus())) {
            throw new IllegalStateException("Nhân sự " + staff.getStaffCode() + " hiện không hoạt động / nghỉ phép!");
        }

        Window wanted = holdWindow(departDate != null ? departDate : Instant.now(), tripType);
        Hold conflict = firstConflict(crewHolds(List.of(staff), excludeBookingId).get(staff.getId()), wanted);
        if (conflict != null) {
            throw new IllegalStateException("Nhân sự " + staff.getStaffCode() + " (" + staff.getRole()
                    + ") không nhận được đơn này: " + holdText(conflict) + ".");
        }
    }

    private boolean isStaffAssignedToBooking(Staff staff, List<BookingAssignment> assignments,
            BookingDetailsPayloadDto payload) {
        for (BookingAssignment ba : assignments) {
            if (ba.getStaffID() != null && Objects.equals(ba.getStaffID().getId(), staff.getId())) {
                return true;
            }
        }
        return payload.getTrips() != null && payload.getTrips().stream().anyMatch(trip -> isStaffAssignedToTrip(trip, staff));
    }

    // Chuyến của nhân sự đã rời trụ sở và chưa xác nhận về (null nếu không có)
    private VehicleTripDto notBackTripOf(BookingDetailsPayloadDto payload, Staff staff, boolean onRoad) {
        if (payload.getTrips() == null) return null;
        return payload.getTrips().stream().filter(trip -> {
            if (!isStaffAssignedToTrip(trip, staff)) return false;
            Map<String, Object> hr = trip.getHomeReturn();
            return hr != null ? hr.get("backAt") == null : onRoad;
        }).findFirst().orElse(null);
    }

    private boolean isStaffAssignedToTrip(VehicleTripDto trip, Staff staff) {
        String role = staff.getRole() == null ? "" : staff.getRole().toUpperCase(Locale.ROOT);
        String assignedId = "DRIVER".equals(role) ? trip.getDriverId()
                : "ESCORT".equals(role) ? trip.getEscortId() : null;
        if (assignedId == null) return false;
        String staffCode = staff.getStaffCode() != null ? staff.getStaffCode() : fallbackStaffCode(staff);
        return sameStaffIdentifier(assignedId, staffCode)
                || (staff.getId() != null && sameStaffIdentifier(assignedId, staff.getId().toString()));
    }

    private boolean sameStaffIdentifier(String first, String second) {
        String normalizedValue = first.toUpperCase(Locale.ROOT);
        String normalizedExpected = second.toUpperCase(Locale.ROOT);
        if (normalizedValue.equals(normalizedExpected)) return true;
        String normalizedFirst = normalizedValue.replaceFirst("^(TX|DR|NV|ES|HT)-", "").replaceAll("\\D", "");
        String normalizedSecond = normalizedExpected.replaceFirst("^(TX|DR|NV|ES|HT)-", "").replaceAll("\\D", "");
        return !normalizedFirst.isEmpty() && normalizedFirst.equals(normalizedSecond);
    }
}
