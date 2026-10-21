package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.fleet.CrewResponse;
import com.example.racehorse_transport.dto.fleet.VehicleResponse;
import com.example.racehorse_transport.dto.fleet.VehicleRequest;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.FleetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

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
            "in_transit",
            "incident_reported",
            "pending_emergency_approval",
            "emergency_plan_active");

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

    @Override
    public List<VehicleResponse> getAllVehicles() {
        List<Booking> holdingBookings = getHoldingBookings();
        Set<Integer> busyVehicleIds = new HashSet<>();
        for (Booking b : holdingBookings) {
            List<BookingVehicle> bvs = bookingVehicleRepository.findByBookingID_Id(b.getId());
            for (BookingVehicle bv : bvs) {
                if (bv.getVehicleID() != null && bv.getVehicleID().getId() != null) {
                    busyVehicleIds.add(bv.getVehicleID().getId());
                }
            }
        }

        return vehicleRepository.findAll().stream().map(v -> {
            VehicleResponse res = mapToVehicleResponse(v);
            if (busyVehicleIds.contains(v.getId()) || isBusyStatus(v.getStatus())) {
                res.setStatus("BUSY");
                res.setAvailable(false);
                res.setBusyReason("Xe đang được gán cho chuyến đi");
            }
            return res;
        }).toList();
    }

    @Override
    public List<VehicleResponse> getAvailableVehicles(Instant departDate, String tripType) {
        Instant newDepart = departDate != null ? departDate : Instant.now();
        boolean newIsInter = "international".equalsIgnoreCase(tripType);
        int newDaysBefore = newIsInter ? 2 : 1;
        int newDaysAfter = newIsInter ? 4 : 2;
        Instant newStart = newDepart.minus(newDaysBefore, ChronoUnit.DAYS);
        Instant newEnd = newDepart.plus(newDaysAfter, ChronoUnit.DAYS);

        List<Vehicle> allVehicles = vehicleRepository.findAll();
        List<Booking> holdingBookings = getHoldingBookings();

        // Bản đồ: VehicleID -> Lý do bận
        Map<Integer, String> busyMap = new HashMap<>();

        for (Booking booking : holdingBookings) {
            Instant bookingDepart = booking.getDepartureDate() != null ? booking.getDepartureDate()
                    : booking.getBookingDate();
            if (bookingDepart == null)
                continue;

            // Tính khoảng đệm giữ xe khứ hồi về trụ sở VN dựa theo chính đơn hàng đang giữ
            // xe
            boolean existingIsInter = booking.getBookingType() != null
                    && booking.getBookingType().equalsIgnoreCase("international");
            int existingDaysBefore = existingIsInter ? 2 : 1;
            int existingDaysAfter = existingIsInter ? 4 : 2;

            Instant busyStart = bookingDepart.minus(existingDaysBefore, ChronoUnit.DAYS);
            Instant busyEnd = bookingDepart.plus(existingDaysAfter, ChronoUnit.DAYS);

            // Kiểm tra hai khoảng thời gian có giao nhau (overlap) hay không
            if (!newEnd.isBefore(busyStart) && !newStart.isAfter(busyEnd)) {
                List<BookingVehicle> bvs = bookingVehicleRepository.findByBookingID_Id(booking.getId());
                for (BookingVehicle bv : bvs) {
                    if (bv.getVehicleID() != null) {
                        String tripTag = existingIsInter ? "quốc tế" : "nội địa";
                        busyMap.put(bv.getVehicleID().getId(),
                                "Đang chạy đơn BK-" + String.format("%03d", booking.getId()) + " (" + tripTag
                                        + ", dự kiến về bãi: " + busyEnd + ")");
                    }
                }
            }
        }

        return allVehicles.stream().map(v -> {
            VehicleResponse res = mapToVehicleResponse(v);
            if (isMaintenanceStatus(v.getStatus())) {
                res.setAvailable(false);
                res.setBusyReason("Xe đang bảo dưỡng định kỳ");
            } else if (isBusyStatus(v.getStatus())) {
                res.setStatus("BUSY");
                res.setAvailable(false);
                res.setBusyReason("Xe đang được gán cho chuyến đi");
            } else if (busyMap.containsKey(v.getId())) {
                res.setAvailable(false);
                res.setBusyReason(busyMap.get(v.getId()));
            } else {
                res.setAvailable(true);
                res.setBusyReason(null);
            }
            return res;
        }).toList();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public VehicleResponse createVehicle(VehicleRequest request) {
        String plate = request.getPlate().trim().toUpperCase(Locale.ROOT);
        if (vehicleRepository.findByLicensePlate(plate).isPresent()) {
            throw new IllegalArgumentException("Biển số xe đã tồn tại.");
        }
        Vehicle vehicle = new Vehicle();
        applyVehicleRequest(vehicle, request, plate);
        return mapToVehicleResponse(vehicleRepository.save(vehicle));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public VehicleResponse updateVehicle(String identifier, VehicleRequest request) {
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

    private void applyVehicleRequest(Vehicle vehicle, VehicleRequest request, String plate) {
        vehicle.setVehicleType(request.getType().trim());
        vehicle.setCapacity(request.getCapacity());
        vehicle.setLicensePlate(plate);
        vehicle.setStatus(request.getStatus() == null || request.getStatus().isBlank()
                ? "ACTIVE"
                : request.getStatus().trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public List<CrewResponse> getAllCrew(String role) {
        List<Staff> staffList = role != null && !role.isBlank() && !role.equalsIgnoreCase("ALL")
                ? staffRepository.findByRoleIgnoreCase(role)
                : staffRepository.findAll();
        return staffList.stream().map(this::mapToCrewResponse).toList();
    }

    @Override
    public List<CrewResponse> getAvailableCrew(Instant departDate, String role) {
        Instant newDepart = departDate != null ? departDate : Instant.now();
        Instant newStart = newDepart.minus(1, ChronoUnit.DAYS);
        Instant newEnd = newDepart.plus(2, ChronoUnit.DAYS);

        List<Staff> staffList = role != null && !role.isBlank() && !role.equalsIgnoreCase("ALL")
                ? staffRepository.findByRoleIgnoreCase(role)
                : staffRepository.findAll();

        List<Booking> holdingBookings = getHoldingBookings();

        // Bản đồ: Staff UserID -> Lý do bận
        Map<Integer, String> busyMap = new HashMap<>();

        for (Booking booking : holdingBookings) {
            Instant bookingDepart = booking.getDepartureDate() != null ? booking.getDepartureDate()
                    : booking.getBookingDate();
            if (bookingDepart == null)
                continue;

            boolean existingIsInter = booking.getBookingType() != null
                    && booking.getBookingType().equalsIgnoreCase("international");
            int existingDaysBefore = existingIsInter ? 2 : 1;
            int existingDaysAfter = existingIsInter ? 4 : 2;

            Instant busyStart = bookingDepart.minus(existingDaysBefore, ChronoUnit.DAYS);
            Instant busyEnd = bookingDepart.plus(existingDaysAfter, ChronoUnit.DAYS);

            // Kiểm tra hai khoảng thời gian có giao nhau hay không
            if (!newEnd.isBefore(busyStart) && !newStart.isAfter(busyEnd)) {
                List<BookingAssignment> assignments = bookingAssignmentRepository.findByBookingID_Id(booking.getId());
                for (BookingAssignment ba : assignments) {
                    if (ba.getStaffID() != null) {
                        String tripTag = existingIsInter ? "quốc tế" : "nội địa";
                        busyMap.put(ba.getStaffID().getId(),
                                "Đang phục vụ đơn BK-" + String.format("%03d", booking.getId()) + " (" + tripTag
                                        + ", về dự kiến: " + busyEnd + ")");
                    }
                }
            }
        }

        return staffList.stream().map(s -> {
            CrewResponse res = mapToCrewResponse(s);
            if (!"ACTIVE".equalsIgnoreCase(s.getEmploymentStatus())) {
                res.setAvailable(false);
                res.setBusyReason("Tài khoản nhân sự đang tạm khóa / nghỉ phép");
            } else if (busyMap.containsKey(s.getId())) {
                res.setAvailable(false);
                res.setBusyReason(busyMap.get(s.getId()));
            } else {
                res.setAvailable(true);
                res.setBusyReason(null);
            }
            return res;
        }).toList();
    }

    private List<Booking> getHoldingBookings() {
        return bookingRepository.findAll().stream()
                .filter(b -> b.getStatus() != null && HOLDING_STATUSES.contains(b.getStatus().toLowerCase()))
                .toList();
    }

    private VehicleResponse mapToVehicleResponse(Vehicle v) {
        return VehicleResponse.builder()
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

    private CrewResponse mapToCrewResponse(Staff s) {
        String fullName = s.getUser() != null ? s.getUser().getUsername() : "Nhân viên " + s.getId();
        String email = s.getUser() != null ? s.getUser().getEmail() : "";
        return CrewResponse.builder()
                .userId(s.getId())
                .staffCode(s.getStaffCode() != null ? s.getStaffCode() : "NV-" + s.getId())
                .name(fullName)
                .role(s.getRole())
                .phone(s.getPhone())
                .email(email)
                .employmentStatus(s.getEmploymentStatus())
                .isAvailable("ACTIVE".equalsIgnoreCase(s.getEmploymentStatus()))
                .busyReason(null)
                .build();
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
            } catch (Exception ignored) {
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

        Instant newDepart = departDate != null ? departDate : Instant.now();
        boolean newIsInter = "international".equalsIgnoreCase(tripType);
        int newDaysBefore = newIsInter ? 2 : 1;
        int newDaysAfter = newIsInter ? 4 : 2;
        Instant newStart = newDepart.minus(newDaysBefore, ChronoUnit.DAYS);
        Instant newEnd = newDepart.plus(newDaysAfter, ChronoUnit.DAYS);

        List<Booking> holdingBookings = getHoldingBookings();
        for (Booking b : holdingBookings) {
            if (excludeBookingId != null && excludeBookingId.equals(b.getId())) {
                continue;
            }
            Instant bookingDepart = b.getDepartureDate() != null ? b.getDepartureDate() : b.getBookingDate();
            if (bookingDepart == null)
                continue;

            boolean existingIsInter = b.getBookingType() != null
                    && b.getBookingType().equalsIgnoreCase("international");
            int existingDaysBefore = existingIsInter ? 2 : 1;
            int existingDaysAfter = existingIsInter ? 4 : 2;

            Instant busyStart = bookingDepart.minus(existingDaysBefore, ChronoUnit.DAYS);
            Instant busyEnd = bookingDepart.plus(existingDaysAfter, ChronoUnit.DAYS);

            // Kiểm tra hai khoảng thời gian có giao nhau hay không
            if (!newEnd.isBefore(busyStart) && !newStart.isAfter(busyEnd)) {
                List<BookingVehicle> bvs = bookingVehicleRepository.findByBookingID_Id(b.getId());
                for (BookingVehicle bv : bvs) {
                    if (bv.getVehicleID() != null && bv.getVehicleID().getId().equals(vehicleId)) {
                        String tripTag = existingIsInter ? "quốc tế" : "nội địa";
                        throw new IllegalStateException("Xe " + vehicle.getLicensePlate() + " ("
                                + vehicle.getVehicleName() + ") trùng lịch với đơn BK-"
                                + String.format("%03d", b.getId()) + " (" + tripTag + ", khứ hồi về trụ sở VN đến "
                                + busyEnd + ")!");
                    }
                }
            }
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
            } catch (Exception ignored) {
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

        Instant newDepart = departDate != null ? departDate : Instant.now();
        boolean newIsInter = "international".equalsIgnoreCase(tripType);
        int newDaysBefore = newIsInter ? 2 : 1;
        int newDaysAfter = newIsInter ? 4 : 2;
        Instant newStart = newDepart.minus(newDaysBefore, ChronoUnit.DAYS);
        Instant newEnd = newDepart.plus(newDaysAfter, ChronoUnit.DAYS);

        List<Booking> holdingBookings = getHoldingBookings();
        for (Booking b : holdingBookings) {
            if (excludeBookingId != null && excludeBookingId.equals(b.getId())) {
                continue;
            }
            Instant bookingDepart = b.getDepartureDate() != null ? b.getDepartureDate() : b.getBookingDate();
            if (bookingDepart == null)
                continue;

            boolean existingIsInter = b.getBookingType() != null
                    && b.getBookingType().equalsIgnoreCase("international");
            int existingDaysBefore = existingIsInter ? 2 : 1;
            int existingDaysAfter = existingIsInter ? 4 : 2;

            Instant busyStart = bookingDepart.minus(existingDaysBefore, ChronoUnit.DAYS);
            Instant busyEnd = bookingDepart.plus(existingDaysAfter, ChronoUnit.DAYS);

            // Kiểm tra hai khoảng thời gian có giao nhau hay không
            if (!newEnd.isBefore(busyStart) && !newStart.isAfter(busyEnd)) {
                List<BookingAssignment> assignments = bookingAssignmentRepository.findByBookingID_Id(b.getId());
                for (BookingAssignment ba : assignments) {
                    if (ba.getStaffID() != null && ba.getStaffID().getId().equals(staffId)) {
                        String tripTag = existingIsInter ? "quốc tế" : "nội địa";
                        throw new IllegalStateException("Nhân sự " + staff.getStaffCode() + " (" + staff.getRole()
                                + ") đang được giao phục vụ đơn BK-"
                                + String.format("%03d", b.getId()) + " (" + tripTag + ", khứ hồi về trụ sở VN đến "
                                + busyEnd + ")!");
                    }
                }
            }
        }
    }
}
