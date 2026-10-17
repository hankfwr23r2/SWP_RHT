package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.booking.CreateBookingRequest;
import com.example.racehorse_transport.dto.booking.PublicTrackingResponse;
import com.example.racehorse_transport.dto.booking.PartyContactDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.BookingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.dto.coordinator.CoordinatorPlanRequest;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.dto.manager.*;
import com.example.racehorse_transport.service.FleetService;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final HorseRepository horseRepository;
    private final SystemLogRepository systemLogRepository;
    private final BookingVehicleRepository bookingVehicleRepository;
    private final BookingAssignmentRepository bookingAssignmentRepository;
    private final QuotationRepository quotationRepository;
    private final PaymentRepository paymentRepository;
    private final FleetService fleetService;
    private final LogRepository logRepository;
    private final VehicleRepository vehicleRepository;
    private final StaffRepository staffRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Booking> findAll() {
        return bookingRepository.findAll();
    }

    @Override
    public Optional<Booking> findById(Integer id) {
        return bookingRepository.findById(id);
    }

    @Override
    public Booking save(Booking entity) {
        return bookingRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        bookingRepository.deleteById(id);
    }

    // =========================================================================
    // PHẦN VIỆC CỦA DEV 1 (Lead): Quản lý Đơn hàng (Flow 1)
    // =========================================================================

    @Override
    @Transactional
    public BookingResponse createBooking(String customerUsername, CreateBookingRequest request) {
        // 1. Tìm thông tin User
        User user = userRepository.findByUsernameOrEmail(customerUsername, customerUsername)
                .orElseThrow(
                        () -> new IllegalArgumentException("Không tìm thấy thông tin người dùng: " + customerUsername));

        // 2. Tìm hoặc tự động tạo Customer profile
        Customer customer = customerRepository.findById(user.getId())
                .orElseGet(() -> {
                    Customer c = new Customer();
                    c.setUser(user);
                    c.setFullName(
                            request.getConsignor() != null ? request.getConsignor().getName() : user.getUsername());
                    c.setPhone(request.getConsignor() != null ? request.getConsignor().getPhone() : "");
                    return customerRepository.save(c);
                });

        // 2b. Kiểm tra quy tắc PRD mục 8.2: Chặn tạo đơn mới nếu khách có đơn quá hạn thanh toán quyết toán (payment_overdue)
        List<Booking> customerBookings = bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(customer.getId());
        if (customerBookings != null) {
            boolean hasOverdue = customerBookings.stream()
                    .anyMatch(b -> "payment_overdue".equalsIgnoreCase(b.getStatus()));
            if (hasOverdue) {
                throw new IllegalStateException("Bạn có đơn hàng đang quá hạn thanh toán quyết toán (payment_overdue). Vui lòng thanh toán trước khi tạo đơn mới!");
            }
        }

        // 3. Chuyển đổi timestamp ngày khởi hành
        Instant departureInstant = request.getDepartAt() != null
                ? Instant.ofEpochMilli(request.getDepartAt())
                : Instant.now().plusSeconds(86400 * 3);

        // 4. Serialize cấu hình chi tiết ngựa và khởi tạo payload vào detailsJson
        String detailsJson = null;
        try {
            BookingDetailsPayload initialPayload = new BookingDetailsPayload();
            initialPayload.setHorses(request.getHorses() != null ? request.getHorses() : new ArrayList<>());
            Map<String, Object> initialMedical = new HashMap<>();
            initialMedical.put("status", "pending");
            initialPayload.setMedical(initialMedical);
            List<Map<String, Object>> hist = new ArrayList<>();
            Map<String, Object> hEntry = new HashMap<>();
            hEntry.put("time", System.currentTimeMillis());
            hEntry.put("actor", customer != null && customer.getUser() != null ? customer.getUser().getUsername() : customerUsername);
            hEntry.put("text", "Gửi yêu cầu đặt đơn");
            hist.add(hEntry);
            initialPayload.setHistory(hist);
            detailsJson = objectMapper.writeValueAsString(initialPayload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể lưu chi tiết đơn vận chuyển.", e);
        }

        // 5. Tạo đơn hàng Booking mới
        Booking booking = Booking.builder()
                .customerID(customer)
                .bookingDate(Instant.now())
                .departureDate(departureInstant)
                .status("pending_intake")
                .bookingType(request.getType() != null ? request.getType().toLowerCase() : "domestic")
                .consignorName(request.getConsignor() != null ? request.getConsignor().getName().trim() : "")
                .consignorPhone(request.getConsignor() != null ? request.getConsignor().getPhone().trim() : "")
                .consigneeName(request.getConsignee() != null ? request.getConsignee().getName().trim() : "")
                .consigneePhone(request.getConsignee() != null ? request.getConsignee().getPhone().trim() : "")
                .consignorIdNumber(request.getConsignor() != null ? trimToNull(request.getConsignor().getIdNumber()) : null)
                .consignorAddress(request.getConsignor() != null ? trimToNull(request.getConsignor().getAddress()) : null)
                .consigneeIdNumber(request.getConsignee() != null ? trimToNull(request.getConsignee().getIdNumber()) : null)
                .consigneeAddress(request.getConsignee() != null ? trimToNull(request.getConsignee().getAddress()) : null)
                .pickupAddress(request.getOriginName() != null ? request.getOriginName().trim() : "")
                .dropoffAddress(request.getDestName() != null ? request.getDestName().trim() : "")
                .totalHorses(request.getHorses() != null ? request.getHorses().size() : 1)
                .detailsJson(detailsJson)
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        // 6. Xác nhận các ngựa được chọn thuộc chính khách hàng này.
        // Hồ sơ ngựa đã được tạo ở luồng quản lý hồ sơ, không nhân bản khi tạo booking.
        if (request.getHorses() != null) {
            for (BookingHorseDto hDto : request.getHorses()) {
                if (hDto.getHorseId() == null || hDto.getHorseId().isBlank()) {
                    throw new IllegalArgumentException("Mỗi ngựa trong đơn phải được chọn từ Hồ sơ ngựa.");
                }
                String numericId = hDto.getHorseId().replaceAll("\\D", "");
                if (numericId.isBlank()) {
                    throw new IllegalArgumentException("Mã hồ sơ ngựa không hợp lệ: " + hDto.getHorseId());
                }
                Horse horse = horseRepository.findById(Integer.valueOf(numericId))
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ ngựa " + hDto.getHorseId()));
                if (horse.getCustomerID() == null || !customer.getId().equals(horse.getCustomerID().getId())) {
                    throw new IllegalArgumentException("Hồ sơ ngựa " + hDto.getHorseId() + " không thuộc tài khoản của bạn.");
                }
            }
        }

        // 7. Ghi Audit Log vào SYSTEM_LOG
        SystemLog sysLog = SystemLog.builder()
                .actionbyUserid(user)
                .actionType("CREATE_BOOKING")
                .targetTable("BOOKING")
                .targetRecordID(savedBooking.getId())
                .newData("Tạo đơn vận chuyển " + savedBooking.getTotalHorses() + " con ngựa ("
                        + savedBooking.getPickupAddress() + " -> " + savedBooking.getDropoffAddress() + ")")
                .createdAt(Instant.now())
                .build();
        systemLogRepository.save(sysLog);

        log.info("Successfully created booking ID={} for user={}", savedBooking.getId(), customerUsername);
        return mapToResponse(savedBooking);
    }

    @Override
    public List<BookingResponse> getCustomerBookings(String customerUsername) {
        User user = userRepository.findByUsernameOrEmail(customerUsername, customerUsername)
                .orElseThrow(
                        () -> new IllegalArgumentException("Không tìm thấy thông tin người dùng: " + customerUsername));

        return bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(user.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public BookingResponse getBookingById(Integer id, String customerUsername) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng có ID: " + id));

        if (customerUsername != null) {
            User owner = booking.getCustomerID() != null ? booking.getCustomerID().getUser() : null;
            boolean ownsBooking = owner != null
                    && (customerUsername.equals(owner.getUsername()) || customerUsername.equals(owner.getEmail()));
            if (!ownsBooking) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Bạn không có quyền xem đơn hàng này.");
            }
        }

        return mapToResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse resubmitBooking(String bookingIdentifier, String customerUsername) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + bookingIdentifier));

        User owner = booking.getCustomerID() != null ? booking.getCustomerID().getUser() : null;
        boolean ownsBooking = owner != null && (customerUsername.equals(owner.getUsername())
                || customerUsername.equals(owner.getEmail()));
        if (!ownsBooking) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền với đơn này.");
        }
        if (!"resubmission_requested".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Đơn này không chờ khách bổ sung hồ sơ.");
        }

        BookingDetailsPayload payload = extractPayload(booking);
        Map<String, Object> medical = new HashMap<>();
        if (payload.getMedical() instanceof Map<?, ?> current) {
            current.forEach((key, value) -> medical.put(String.valueOf(key), value));
        }
        medical.put("status", "pending");
        medical.put("resubmittedAt", System.currentTimeMillis());
        medical.remove("reason");
        medical.remove("items");
        medical.remove("requestedAt");
        medical.remove("requestedBy");
        payload.setMedical(medical);
        booking.setStatus("under_review");
        appendHistory(payload, customerUsername, "Khách đã bổ sung hồ sơ và gửi lại Kiểm dịch viên thẩm định");
        savePayload(booking, payload);

        return mapToResponse(bookingRepository.save(booking));
    }

    @Override
    public List<BookingResponse> getBookingsByStatus(String status) {
        if (status == null || status.trim().isEmpty() || "ALL".equalsIgnoreCase(status)) {
            return bookingRepository.findAllByOrderByBookingDateDesc()
                    .stream()
                    .map(this::mapToResponse)
                    .toList();
        }
        return bookingRepository.findByStatusOrderByBookingDateDesc(status)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private static final Set<String> FIELD_STATUSES = Set.of(
            "waybill_issued", "clearance_in_progress", "clearance_done", "ready_for_pickup",
            "en_route_to_pickup", "in_transit", "incident_reported",
            "pending_emergency_approval", "emergency_plan_active", "delivered_pending_settlement"
    );

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyFieldBookings(String username, String role) {
        Staff me = staffRepository.findByUser_Username(username)
                .or(() -> staffRepository.findAll().stream()
                        .filter(s -> s.getUser() != null && (
                                username.equalsIgnoreCase(s.getUser().getUsername()) ||
                                username.equalsIgnoreCase(s.getUser().getEmail())))
                        .findFirst())
                .orElse(null);
        String myCode = (me != null && me.getStaffCode() != null) ? me.getStaffCode() : "";
        String myId = (me != null && me.getId() != null) ? String.valueOf(me.getId()) : "";
        String myName = (me != null && me.getUser() != null && me.getUser().getUsername() != null) ? me.getUser().getUsername() : "";

        Set<Integer> assignedBookingIds = new HashSet<>();
        if (me != null) {
            bookingAssignmentRepository.findByStaffID_Id(me.getId()).forEach(a -> {
                if (a.getBookingID() != null) {
                    assignedBookingIds.add(a.getBookingID().getId());
                }
            });
        }

        boolean isEscort = "ESCORT".equalsIgnoreCase(role);
        return bookingRepository.findAllByOrderByBookingDateDesc().stream()
                .filter(b -> b.getStatus() != null && FIELD_STATUSES.contains(b.getStatus()))
                .filter(b -> {
                    if (assignedBookingIds.contains(b.getId())) {
                        return true;
                    }
                    if (b.getDetailsJson() == null) return false;
                    try {
                        BookingDetailsPayload payload = objectMapper.readValue(b.getDetailsJson(), BookingDetailsPayload.class);
                        if (payload.getTrips() == null) return false;
                        return payload.getTrips().stream().anyMatch(t -> {
                            String assigned = isEscort ? t.getEscortId() : t.getDriverId();
                            if (assigned == null || assigned.isBlank()) return false;
                            return assigned.equalsIgnoreCase(myCode)
                                    || assigned.equalsIgnoreCase(myId)
                                    || assigned.equalsIgnoreCase(username)
                                    || (!myName.isBlank() && assigned.equalsIgnoreCase(myName));
                        });
                    } catch (Exception e) {
                        return false;
                    }
                })
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getAssignedBookings(String username) {
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin người dùng: " + username));
        return bookingAssignmentRepository.findByStaffID_Id(user.getId()).stream()
                .filter(assignment -> assignment.getBookingID() != null)
                .filter(assignment -> "ASSIGNED".equalsIgnoreCase(assignment.getStatus()))
                .map(assignment -> assignment.getBookingID())
                .filter(booking -> booking.getStatus() != null
                        && !Set.of("cancelled", "rejected", "completed").contains(booking.getStatus().toLowerCase()))
                .distinct()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public BookingResponse activateBooking(String bookingIdentifier, com.example.racehorse_transport.dto.manager.ActivateBookingRequest request) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + bookingIdentifier));

        if (!"pending_intake".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Đơn hàng không ở bước chờ tiếp nhận (pending_intake)!");
        }

        BookingDetailsPayload payload = extractPayload(booking);

        Map<String, Object> intakeMap = new HashMap<>();
        intakeMap.put("at", System.currentTimeMillis());
        intakeMap.put("by", request != null && request.getBy() != null ? request.getBy() : "Quản lý");
        intakeMap.put("specialist", request != null ? request.getSpecialist() : null);
        intakeMap.put("coordinator", request != null ? request.getCoordinator() : null);
        payload.setIntake(intakeMap);

        if (payload.getMedical() == null) {
            Map<String, Object> med = new HashMap<>();
            med.put("status", "pending");
            payload.setMedical(med);
        }

        String spName = (request != null && request.getSpecialist() != null && request.getSpecialist().get("name") != null)
                ? String.valueOf(request.getSpecialist().get("name")) : "Chuyên viên kiểm dịch";
        String coName = (request != null && request.getCoordinator() != null && request.getCoordinator().get("name") != null)
                ? String.valueOf(request.getCoordinator().get("name")) : "Điều phối viên";

        appendHistory(payload, request != null && request.getBy() != null ? request.getBy() : "Quản lý",
                "Tiếp nhận đơn. Giao " + spName + " (kiểm dịch) và " + coName + " (điều phối)");

        booking.setStatus("under_review");
        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);

        logAction("MANAGER_ACTIVATE_BOOKING", saved.getId(), "Quản lý tiếp nhận đơn BK-" + saved.getId() + " và kích hoạt thẩm định song song");
        log.info("Manager activated booking ID={} -> status=under_review", saved.getId());
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponse rejectBooking(String bookingIdentifier, com.example.racehorse_transport.dto.manager.RejectBookingRequest request) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + bookingIdentifier));

        String role = request != null && request.getRole() != null ? request.getRole().toLowerCase() : "manager";
        if ("manager".equals(role) && !"pending_intake".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Quản lý chỉ từ chối được đơn đang chờ tiếp nhận (pending_intake)!");
        }
        if ("coordinator".equals(role) && !"under_review".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Điều phối viên chỉ từ chối được đơn đang thẩm định (under_review)!");
        }

        BookingDetailsPayload payload = extractPayload(booking);
        String reason = (request != null && request.getReason() != null) ? request.getReason().trim() : "Từ chối đơn";

        Map<String, Object> rejectionMap = new HashMap<>();
        rejectionMap.put("at", System.currentTimeMillis());
        rejectionMap.put("by", request != null && request.getBy() != null ? request.getBy() : "Quản lý");
        rejectionMap.put("role", role);
        rejectionMap.put("reason", reason);

        if (payload.getHistory() == null) {
            payload.setHistory(new ArrayList<>());
        }
        appendHistory(payload, request != null && request.getBy() != null ? request.getBy() : "Quản lý",
                "Từ chối đơn: " + reason);

        payload.setRejection(rejectionMap);
        booking.setStatus("rejected");
        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);

        logAction("REJECT_BOOKING", saved.getId(), "Từ chối đơn BK-" + saved.getId() + " bởi " + role + ": " + reason);
        log.info("Booking ID={} rejected by {}: reason={}", saved.getId(), role, reason);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponse confirmCoordinatorPlan(String bookingIdentifier, CoordinatorPlanRequest request) {
        if (bookingIdentifier == null || bookingIdentifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã đơn hàng không được để trống!");
        }

        // 1. Phân giải Booking ID (chấp nhận "BK-001", "1", "ORD-2026-0001", v.v.)
        Integer bookingId;
        String cleanId = bookingIdentifier.trim();
        try {
            if (cleanId.toUpperCase().startsWith("BK-")) {
                bookingId = Integer.parseInt(cleanId.substring(3).trim());
            } else if (cleanId.toUpperCase().startsWith("ORD-")) {
                String[] parts = cleanId.split("-");
                bookingId = Integer.parseInt(parts[parts.length - 1].trim());
            } else {
                bookingId = Integer.parseInt(cleanId);
            }
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Định dạng mã đơn không hợp lệ: " + bookingIdentifier, ex);
        }

        // 2. Tìm đơn hàng
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng ID: " + bookingIdentifier));

        if (request.getTrips() == null || request.getTrips().isEmpty()) {
            throw new IllegalArgumentException("Phương án điều phối phải có ít nhất 1 chuyến xe!");
        }

        // 3. Kiểm tra tính khả dụng & tải trọng của từng xe
        Instant departDate = booking.getDepartureDate() != null ? booking.getDepartureDate() : Instant.now();
        List<BookingVehicle> newVehicles = new ArrayList<>();

        for (int i = 0; i < request.getTrips().size(); i++) {
            VehicleTripDto trip = request.getTrips().get(i);
            if (trip.getVehicleId() == null || trip.getVehicleId().trim().isEmpty()) {
                throw new IllegalArgumentException("Chuyến thứ " + (i + 1) + " chưa được chọn xe!");
            }
            if (trip.getHorseIds() == null || trip.getHorseIds().isEmpty()) {
                throw new IllegalArgumentException("Chuyến thứ " + (i + 1) + " phải chở ít nhất 1 ngựa!");
            }

            Vehicle vehicle = fleetService.findVehicleByIdOrCode(trip.getVehicleId());

            // Kiểm tra tải trọng ngăn xe
            if (trip.getHorseIds().size() > vehicle.getCapacity()) {
                throw new IllegalArgumentException(
                        "Xe " + vehicle.getLicensePlate() + " chỉ có " + vehicle.getCapacity()
                                + " ngăn, không thể chở " + trip.getHorseIds().size() + " con ngựa!");
            }

            // Kiểm tra lịch bận & khứ hồi về trụ sở VN
            fleetService.validateVehicleAvailability(vehicle.getId(), departDate, booking.getBookingType(),
                    booking.getId());

            // Chuẩn hóa tripId
            if (trip.getTripId() == null || trip.getTripId().trim().isEmpty()) {
                trip.setTripId("TRP-" + String.format("%03d", booking.getId()) + "-" + (i + 1));
            }
            // Chuẩn hóa vehicleId đồng bộ với Frontend (VH-XXX hoặc vehicleCode)
            String vCode = vehicle.getVehicleCode() != null && !vehicle.getVehicleCode().isBlank()
                    ? vehicle.getVehicleCode()
                    : ("VH-" + String.format("%03d", vehicle.getId()));
            trip.setVehicleId(vCode);

            BookingVehicle bv = BookingVehicle.builder()
                    .id(new BookingVehicleId(booking.getId(), vehicle.getId()))
                    .bookingID(booking)
                    .vehicleID(vehicle)
                    .assignAt(Instant.now())
                    .build();
            newVehicles.add(bv);
        }

        // 4. Lưu liên kết xe vào bảng BOOKING_VEHICLE
        bookingVehicleRepository.deleteByBookingID_Id(booking.getId());
        bookingVehicleRepository.flush();
        bookingVehicleRepository.saveAll(newVehicles);

        // 5. Cập nhật detailsJson của Booking
        BookingDetailsPayload payload = new BookingDetailsPayload();
        if (booking.getDetailsJson() != null && !booking.getDetailsJson().trim().isEmpty()) {
            String existingJson = booking.getDetailsJson().trim();
            try {
                if (existingJson.startsWith("{")) {
                    payload = objectMapper.readValue(existingJson, BookingDetailsPayload.class);
                } else if (existingJson.startsWith("[")) {
                    List<BookingHorseDto> horses = objectMapper.readValue(existingJson,
                            new TypeReference<List<BookingHorseDto>>() {
                            });
                    payload.setHorses(horses);
                }
            } catch (Exception ex) {
                log.warn("Error reading existing detailsJson: {}", ex.getMessage());
            }
        }

        payload.setTrips(request.getTrips());
        payload.setRoute(request.getRoute());
        payload.setGate(request.getGate());

        Map<String, Object> planMap = new HashMap<>();
        planMap.put("at", System.currentTimeMillis());
        planMap.put("by", request.getBy() != null ? request.getBy() : "Điều phối viên");
        planMap.put("note", request.getNote() != null ? request.getNote() : "");
        payload.setPlan(planMap);

        if (payload.getHistory() == null) {
            payload.setHistory(new ArrayList<>());
        }
        Map<String, Object> historyEntry = new HashMap<>();
        historyEntry.put("time", System.currentTimeMillis());
        historyEntry.put("actor", request.getBy() != null ? request.getBy() : "Điều phối viên");
        historyEntry.put("text", "Xác nhận " + request.getTrips().size() + " xe và lộ trình di chuyển");
        payload.getHistory().add(historyEntry);

        // 6. Kiểm tra điều kiện đồng bộ chuyển trạng thái (reviewDone gate)
        String medicalStatus = getMedicalStatus(payload);
        boolean medicalApproved = "approved".equalsIgnoreCase(medicalStatus);

        if (medicalApproved) {
            booking.setStatus("pending_commercial");
            appendHistory(payload, "Hệ thống", "Hồ sơ ngựa và phương án xe đều đã duyệt xong -> Chuyển Quản lý duyệt báo giá.");
        } else {
            booking.setStatus("under_review");
            appendHistory(payload, "Hệ thống", "Điều phối viên đã chốt xe và lộ trình. Đang chờ Chuyên viên kiểm dịch thẩm định hồ sơ ngựa.");
        }

        try {
            booking.setDetailsJson(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            log.error("Could not serialize payload: {}", ex.getMessage());
        }

        Booking updatedBooking = bookingRepository.save(booking);

        // 7. Ghi nhật ký hệ thống SYSTEM_LOG
        try {
            String logDesc = medicalApproved
                    ? "Điều phối viên (" + (request.getBy() != null ? request.getBy() : "coordinator") + ") chốt "
                            + request.getTrips().size() + " xe và lộ trình đơn BK-" + updatedBooking.getId()
                            + ", cả 2 bên đã đạt -> chuyển Quản lý duyệt báo giá."
                    : "Điều phối viên (" + (request.getBy() != null ? request.getBy() : "coordinator") + ") chốt "
                            + request.getTrips().size() + " xe và lộ trình đơn BK-" + updatedBooking.getId()
                            + ", đang chờ Chuyên viên kiểm dịch thẩm định hồ sơ ngựa.";
            SystemLog sysLog = SystemLog.builder()
                    .actionType("COORDINATOR_CONFIRM_PLAN")
                    .targetTable("BOOKING")
                    .targetRecordID(updatedBooking.getId())
                    .newData(logDesc)
                    .createdAt(Instant.now())
                    .build();
            systemLogRepository.save(sysLog);
        } catch (Exception ignored) {
        }

        log.info("Coordinator successfully confirmed fleet plan for booking ID={}", updatedBooking.getId());
        return mapToResponse(updatedBooking);
    }

    @Override
    @Transactional
    public BookingResponse assignCrew(String bookingIdentifier, AssignCrewRequest request) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + bookingIdentifier));

        if (!"pending_commercial".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException(
                    "Chỉ phân công kíp xe khi đơn đang ở bước duyệt báo giá (pending_commercial)!");
        }

        if (request.getPicks() == null || request.getPicks().isEmpty()) {
            throw new IllegalArgumentException("Danh sách phân công kíp xe không được để trống!");
        }

        BookingDetailsPayload payload = extractPayload(booking);
        String medicalStatus = getMedicalStatus(payload);
        if (!"approved".equalsIgnoreCase(medicalStatus)) {
            throw new IllegalStateException("Chuyên viên kiểm dịch chưa duyệt đạt hồ sơ ngựa!");
        }
        if (payload.getPlan() == null) {
            throw new IllegalStateException("Điều phối viên chưa chốt phương án xe và lộ trình!");
        }
        List<VehicleTripDto> trips = payload.getTrips();
        if (trips == null || trips.isEmpty()) {
            throw new IllegalStateException("Đơn hàng chưa có phương án xe để phân tài xế và hộ tống!");
        }

        if (request.getPicks().size() != trips.size()) {
            throw new IllegalArgumentException("Cần chọn đủ kíp xe cho toàn bộ " + trips.size() + " chuyến xe!");
        }

        Instant departDate = booking.getDepartureDate() != null ? booking.getDepartureDate() : Instant.now();
        List<BookingAssignment> newAssignments = new ArrayList<>();
        Set<String> usedDrivers = new HashSet<>();
        Set<String> usedEscorts = new HashSet<>();

        for (int i = 0; i < request.getPicks().size(); i++) {
            CrewPickDto pick = request.getPicks().get(i);
            if (pick.getDriverId() == null || pick.getDriverId().trim().isEmpty() ||
                    pick.getEscortId() == null || pick.getEscortId().trim().isEmpty()) {
                throw new IllegalArgumentException("Mỗi xe phải có đủ 1 tài xế và 1 nhân viên hộ tống!");
            }

            if (!usedDrivers.add(pick.getDriverId().trim())) {
                throw new IllegalArgumentException(
                        "Mỗi xe cần một tài xế riêng biệt, không thể chọn 1 tài xế cho 2 xe!");
            }
            if (!usedEscorts.add(pick.getEscortId().trim())) {
                throw new IllegalArgumentException(
                        "Mỗi xe cần một hộ tống riêng biệt, không thể chọn 1 hộ tống cho 2 xe!");
            }

            Staff driver = fleetService.findStaffByIdOrCode(pick.getDriverId(), "DRIVER");
            fleetService.validateCrewAvailability(driver.getId(), departDate, booking.getBookingType(),
                    booking.getId());

            Staff escort = fleetService.findStaffByIdOrCode(pick.getEscortId(), "ESCORT");
            fleetService.validateCrewAvailability(escort.getId(), departDate, booking.getBookingType(),
                    booking.getId());

            int idx = i;
            VehicleTripDto matchedTrip = trips.stream()
                    .filter(t -> t.getTripId() != null && t.getTripId().equalsIgnoreCase(pick.getTripId()))
                    .findFirst()
                    .orElse(trips.get(idx));

            matchedTrip.setDriverId(
                    driver.getStaffCode() != null ? driver.getStaffCode() : String.valueOf(driver.getId()));
            matchedTrip.setEscortId(
                    escort.getStaffCode() != null ? escort.getStaffCode() : String.valueOf(escort.getId()));

            newAssignments.add(BookingAssignment.builder()
                    .bookingID(booking)
                    .staffID(driver)
                    .assignRole("DRIVER")
                    .status("ASSIGNED")
                    .assignedAt(Instant.now())
                    .note("Phân công tài xế lái xe cho chuyến " + matchedTrip.getTripId())
                    .build());

            newAssignments.add(BookingAssignment.builder()
                    .bookingID(booking)
                    .staffID(escort)
                    .assignRole("ESCORT")
                    .status("ASSIGNED")
                    .assignedAt(Instant.now())
                    .note("Phân công hộ tống/thú y chăm sóc ngựa cho chuyến " + matchedTrip.getTripId())
                    .build());
        }

        bookingAssignmentRepository.deleteByBookingID_Id(booking.getId());
        bookingAssignmentRepository.flush();
        bookingAssignmentRepository.saveAll(newAssignments);

        payload.setTrips(trips);
        appendHistory(payload, request.getBy() != null ? request.getBy() : "Quản lý",
                "Chọn tài xế và hộ tống cho " + trips.size() + " xe");
        savePayload(booking, payload);

        Booking updated = bookingRepository.save(booking);

        logAction("ASSIGN_CREW", updated.getId(), "Quản lý (" + request.getBy() + ") phân công "
                + newAssignments.size() + " nhân sự kíp xe cho đơn BK-" + updated.getId());

        log.info("Successfully assigned crew for booking ID={}", updated.getId());
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public BookingResponse sendQuote(String bookingIdentifier, SendQuoteRequest request) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + bookingIdentifier));

        if (!"pending_commercial".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Đơn hàng không ở bước duyệt báo giá (pending_commercial)!");
        }

        BookingDetailsPayload payload = extractPayload(booking);
        String medicalStatus = getMedicalStatus(payload);
        if (!"approved".equalsIgnoreCase(medicalStatus)) {
            throw new IllegalStateException("Chuyên viên kiểm dịch chưa duyệt đạt hồ sơ ngựa!");
        }
        if (payload.getPlan() == null) {
            throw new IllegalStateException("Điều phối viên chưa chốt phương án xe và lộ trình!");
        }
        List<VehicleTripDto> trips = payload.getTrips();
        if (trips == null || trips.isEmpty()) {
            throw new IllegalStateException("Đơn chưa có phương án xe để gửi báo giá!");
        }
        for (VehicleTripDto t : trips) {
            if (t.getDriverId() == null || t.getDriverId().isBlank() || t.getEscortId() == null
                    || t.getEscortId().isBlank()) {
                throw new IllegalStateException(
                        "Cần phân công tài xế và hộ tống cho tất cả các xe trước khi duyệt báo giá!");
            }
        }

        BigDecimal total = request.getTotal() != null ? request.getTotal() : BigDecimal.ZERO;
        BigDecimal deposit = request.getDeposit() != null && request.getDeposit().compareTo(BigDecimal.ZERO) > 0
                ? request.getDeposit()
                : total.multiply(new BigDecimal("0.30"));
        BigDecimal balance = request.getBalance() != null && request.getBalance().compareTo(BigDecimal.ZERO) > 0
                ? request.getBalance()
                : total.subtract(deposit);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(48, ChronoUnit.HOURS); // Hiệu lực 48 giờ

        User sentByUser = null;
        if (request.getBy() != null && !request.getBy().isBlank()) {
            sentByUser = userRepository.findByUsernameOrEmail(request.getBy(), request.getBy()).orElse(null);
        }

        String linesJson = null;
        try {
            Map<String, Object> quoteContent = new HashMap<>();
            quoteContent.put("lines", request.getLines());
            quoteContent.put("adjustments", request.getAdjustments());
            linesJson = objectMapper.writeValueAsString(quoteContent);
        } catch (Exception ex) {
            log.warn("Could not serialize quote lines: {}", ex.getMessage());
        }

        BigDecimal adjustmentSum = BigDecimal.ZERO;
        if (request.getAdjustments() != null) {
            for (QuoteAdjustmentDto adj : request.getAdjustments()) {
                if (adj.getAmount() != null) {
                    adjustmentSum = adjustmentSum.add(adj.getAmount());
                }
            }
        }

        Quotation quotation = Quotation.builder()
                .booking(booking)
                .subtotal(request.getSubtotal() != null ? request.getSubtotal() : total)
                .adjustmentAmount(adjustmentSum)
                .adjustmentNote(request.getAdjustments() != null && !request.getAdjustments().isEmpty()
                        ? request.getAdjustments().get(0).getLabel()
                        : null)
                .totalAmount(total)
                .depositAmount(deposit)
                .balanceAmount(balance)
                .sentAt(now)
                .expiresAt(expiresAt)
                .sentByUser(sentByUser)
                .status("PENDING")
                .quoteLinesJson(linesJson)
                .build();
        quotationRepository.save(quotation);

        Map<String, Object> quoteMap = new HashMap<>();
        quoteMap.put("lines", request.getLines());
        quoteMap.put("adjustments", request.getAdjustments());
        quoteMap.put("subtotal", request.getSubtotal());
        quoteMap.put("total", total);
        quoteMap.put("deposit", deposit);
        quoteMap.put("balance", balance);
        quoteMap.put("sentAt", now.toEpochMilli());
        quoteMap.put("expiresAt", expiresAt.toEpochMilli());
        quoteMap.put("sentBy", request.getBy() != null ? request.getBy() : "Quản lý");
        payload.setQuote(quoteMap);

        appendHistory(payload, request.getBy() != null ? request.getBy() : "Quản lý",
                "Duyệt và phát hành báo giá: Tổng " + total + " VND, Đặt cọc 30%: " + deposit + " VND (Hiệu lực 48h)");
        savePayload(booking, payload);

        booking.setStatus("awaiting_payment");
        Booking updated = bookingRepository.save(booking);

        logAction("SEND_QUOTATION", updated.getId(), "Phát hành báo giá đơn BK-" + updated.getId()
                + " (Tổng: " + total + ", Cọc: " + deposit + ", Hết hạn: " + expiresAt + ")");

        log.info("Successfully issued quote for booking ID={}, status=awaiting_payment", updated.getId());
        return mapToResponse(updated);
    }

    @SuppressWarnings("unchecked")
    @Override
    @Transactional
    public BookingResponse sendBack(String bookingIdentifier, SendBackRequest request) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + bookingIdentifier));

        if (!"pending_commercial".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Chỉ trả lại được đơn đang chờ duyệt báo giá!");
        }

        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Cần ghi lý do trả lại đơn!");
        }

        BookingDetailsPayload payload = extractPayload(booking);
        String label;
        if ("specialist".equalsIgnoreCase(request.getTo())) {
            label = "Kiểm dịch viên duyệt lại hồ sơ ngựa";
            if (payload.getMedical() instanceof Map) {
                ((Map<String, Object>) payload.getMedical()).put("status", "pending");
            }
        } else {
            label = "Điều phối viên làm lại xe và lộ trình";
            bookingVehicleRepository.deleteByBookingID_Id(booking.getId());
            bookingAssignmentRepository.deleteByBookingID_Id(booking.getId());
            payload.setTrips(null);
            payload.setRoute(null);
            payload.setPlan(null);
            payload.setGate(null);
        }

        booking.setStatus("under_review");
        appendHistory(payload, request.getBy() != null ? request.getBy() : "Quản lý",
                "Trả lại " + label + ": " + request.getReason().trim());
        savePayload(booking, payload);

        Booking updated = bookingRepository.save(booking);
        logAction("SEND_BACK", updated.getId(),
                "Quản lý trả đơn BK-" + updated.getId() + " về " + request.getTo() + ": " + request.getReason());
        log.info("Booking ID={} sent back to {}", updated.getId(), request.getTo());
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public BookingResponse resubmitDocuments(String bookingIdentifier, String customerUsername) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + bookingIdentifier));

        if (customerUsername != null) {
            User owner = booking.getCustomerID() != null ? booking.getCustomerID().getUser() : null;
            boolean ownsBooking = owner != null
                    && (customerUsername.equals(owner.getUsername()) || customerUsername.equals(owner.getEmail()));
            if (!ownsBooking) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Bạn không có quyền thao tác trên đơn hàng này.");
            }
        }

        BookingDetailsPayload payload = extractPayload(booking);
        String medicalStatus = getMedicalStatus(payload);
        if (!"resubmit".equalsIgnoreCase(medicalStatus) && !"resubmission_requested".equalsIgnoreCase(medicalStatus)) {
            throw new IllegalStateException("Đơn hàng này hiện không có yêu cầu bổ sung hồ sơ!");
        }

        Map<String, Object> medicalMap = payload.getMedical() instanceof Map<?, ?>
                ? new HashMap<>((Map<String, Object>) payload.getMedical())
                : new HashMap<>();
        medicalMap.put("status", "pending");
        medicalMap.remove("resubmit");
        medicalMap.put("resubmittedAt", System.currentTimeMillis());
        payload.setMedical(medicalMap);

        booking.setStatus("under_review");

        appendHistory(payload, customerUsername != null ? customerUsername : "Khách hàng",
                "Đã bổ sung hồ sơ, gửi lại Kiểm dịch viên");
        savePayload(booking, payload);

        Booking updated = bookingRepository.save(booking);
        logAction("RESUBMIT_DOCUMENTS", updated.getId(),
                "Khách hàng (" + customerUsername + ") gửi lại hồ sơ cho Chuyên viên kiểm dịch duyệt lại");
        log.info("Booking ID={} resubmitted documents by {}", updated.getId(), customerUsername);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public BookingResponse payDeposit(String bookingIdentifier, String customerUsername,
            com.example.racehorse_transport.dto.booking.PayDepositRequest request) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + bookingIdentifier));

        if (!"awaiting_payment".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Đơn hàng không ở bước chờ thanh toán đặt cọc!");
        }

        Quotation quotation = quotationRepository.findTopByBookingIdOrderBySentAtDesc(booking.getId())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy thông tin báo giá của đơn hàng!"));

        Instant now = Instant.now();
        if (quotation.getExpiresAt() != null && now.isAfter(quotation.getExpiresAt())) {
            throw new IllegalStateException("Báo giá đã hết hạn 48 giờ, không thể đặt cọc!");
        }

        BigDecimal payAmount = (request != null && request.getAmount() != null)
                ? request.getAmount()
                : quotation.getDepositAmount();

        String paymentMethod = (request != null && request.getPaymentMethod() != null)
                ? request.getPaymentMethod()
                : "BANK_TRANSFER";

        String transactionCode = (request != null && request.getTransactionCode() != null)
                ? request.getTransactionCode()
                : "DEP-" + String.format("%04d", booking.getId()) + "-" + System.currentTimeMillis();

        Payment payment = Payment.builder()
                .bookingID(booking)
                .amount(payAmount)
                .paymentDate(now)
                .paymentType("DEPOSIT")
                .paymentMethod(paymentMethod)
                .transactionCode(transactionCode)
                .status("COMPLETED")
                .note("Khách hàng thanh toán cọc 30% cho đơn BK-" + booking.getId())
                .build();
        paymentRepository.save(payment);

        quotation.setStatus("ACCEPTED");
        quotationRepository.save(quotation);

        String waybillNo = "VD-2026-" + String.format("%04d", booking.getId());
        booking.setWaybillNo(waybillNo);
        booking.setStatus("waybill_issued");

        BookingDetailsPayload payload = extractPayload(booking);
        Map<String, Object> paymentMap = new HashMap<>();
        paymentMap.put("paidAt", now.toEpochMilli());
        paymentMap.put("amount", payAmount);
        paymentMap.put("reference", transactionCode);
        payload.setPayment(paymentMap);

        appendHistory(payload, customerUsername != null ? customerUsername : "Khách hàng",
                "Đặt cọc 30% (" + payAmount + " VND), cấp Vận đơn " + waybillNo);
        savePayload(booking, payload);

        Booking updated = bookingRepository.save(booking);

        logAction("PAY_DEPOSIT", updated.getId(),
                "Khách hàng thanh toán cọc 30% (" + payAmount + " VND), cấp Vận đơn " + waybillNo);
        log.info("Successfully paid deposit for booking ID={}, waybill={}", updated.getId(), waybillNo);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public BookingResponse payBalance(String bookingIdentifier, String customerUsername,
            com.example.racehorse_transport.dto.booking.PayBalanceRequest request) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + bookingIdentifier));

        // Kiểm tra xem đơn đã cọc 30% chưa
        List<Payment> existingPayments = paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(booking.getId());
        boolean hasDeposit = existingPayments.stream()
                .anyMatch(p -> "DEPOSIT".equalsIgnoreCase(p.getPaymentType()) && "COMPLETED".equalsIgnoreCase(p.getStatus()));
        if (!hasDeposit && booking.getWaybillNo() == null) {
            throw new IllegalStateException("Đơn hàng chưa thanh toán tiền cọc 30%! Vui lòng cọc trước khi thanh toán số dư.");
        }

        boolean alreadyPaidBalance = existingPayments.stream()
                .anyMatch(p -> "BALANCE".equalsIgnoreCase(p.getPaymentType()) && "COMPLETED".equalsIgnoreCase(p.getStatus()));
        if (alreadyPaidBalance) {
            throw new IllegalStateException("Đơn hàng này đã thanh toán đủ 70% còn lại!");
        }

        Quotation quotation = quotationRepository.findTopByBookingIdOrderBySentAtDesc(booking.getId())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy thông tin báo giá của đơn hàng!"));

        Instant now = Instant.now();
        BigDecimal payAmount = (request != null && request.getAmount() != null)
                ? request.getAmount()
                : (quotation.getBalanceAmount() != null ? quotation.getBalanceAmount() : quotation.getTotalAmount().multiply(new BigDecimal("0.7")));

        String paymentMethod = (request != null && request.getPaymentMethod() != null && !request.getPaymentMethod().isBlank())
                ? request.getPaymentMethod()
                : "BANK_TRANSFER";

        String transactionCode = (request != null && request.getTransactionCode() != null && !request.getTransactionCode().isBlank())
                ? request.getTransactionCode()
                : "BAL-" + String.format("%04d", booking.getId()) + "-" + System.currentTimeMillis();

        Payment payment = Payment.builder()
                .bookingID(booking)
                .amount(payAmount)
                .paymentDate(now)
                .paymentType("BALANCE")
                .paymentMethod(paymentMethod)
                .transactionCode(transactionCode)
                .status("COMPLETED")
                .note("Khách hàng thanh toán 70% cước còn lại ngày D cho đơn BK-" + booking.getId())
                .build();
        paymentRepository.save(payment);

        BookingDetailsPayload payload = extractPayload(booking);
        Map<String, Object> paymentMap;
        if (payload.getPayment() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existing = (Map<String, Object>) payload.getPayment();
            paymentMap = existing;
        } else {
            paymentMap = new HashMap<>();
        }
        paymentMap.put("balancePaid", true);
        paymentMap.put("balanceAmount", payAmount);
        paymentMap.put("balancePaidAt", now.toEpochMilli());
        paymentMap.put("balanceTransaction", transactionCode);
        paymentMap.put("balanceMethod", paymentMethod);
        paymentMap.put("isFullyPaidFixedRate", true);
        payload.setPayment(paymentMap);

        appendHistory(payload, customerUsername != null ? customerUsername : "Khách hàng",
                "Thanh toán 70% còn lại (" + payAmount + " VND). Đã đủ điều kiện để xe bắt đầu hành trình.");
        savePayload(booking, payload);
        Booking updated = bookingRepository.save(booking);

        logAction("PAY_BALANCE", updated.getId(),
                "Khách hàng thanh toán 70% còn lại (" + payAmount + " VND) cho đơn BK-" + updated.getId());
        log.info("Successfully paid balance for booking ID={}", updated.getId());
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public BookingResponse rejectQuote(String bookingIdentifier, String customerUsername,
            com.example.racehorse_transport.dto.booking.RejectQuoteRequest request) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + bookingIdentifier));

        if (!"awaiting_payment".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Chỉ từ chối báo giá khi đơn đang ở bước chờ đặt cọc!");
        }

        Optional<Quotation> quoteOpt = quotationRepository.findTopByBookingIdOrderBySentAtDesc(booking.getId());
        quoteOpt.ifPresent(q -> {
            q.setStatus("REJECTED");
            quotationRepository.save(q);
        });

        bookingVehicleRepository.deleteByBookingID_Id(booking.getId());
        bookingAssignmentRepository.deleteByBookingID_Id(booking.getId());

        booking.setStatus("cancelled");
        BookingDetailsPayload payload = extractPayload(booking);

        String reason = request != null && request.getReason() != null ? request.getReason().trim()
                : "Khách hàng không đồng ý báo giá";
        appendHistory(payload, customerUsername != null ? customerUsername : "Khách hàng",
                "Từ chối báo giá: " + reason + ". Đơn đã hủy, giải phóng xe và kíp xe.");
        savePayload(booking, payload);

        Booking updated = bookingRepository.save(booking);
        logAction("REJECT_QUOTE", updated.getId(),
                "Khách hàng từ chối báo giá đơn BK-" + updated.getId() + ": " + reason);
        log.info("Booking ID={} cancelled by customer rejecting quote", updated.getId());
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public int releaseExpiredQuotations() {
        List<Booking> awaitingBookings = bookingRepository.findByStatusOrderByBookingDateAsc("awaiting_payment");
        int count = 0;
        Instant now = Instant.now();

        for (Booking booking : awaitingBookings) {
            Optional<Quotation> quoteOpt = quotationRepository.findTopByBookingIdOrderBySentAtDesc(booking.getId());
            if (quoteOpt.isPresent()) {
                Quotation q = quoteOpt.get();
                if (q.getExpiresAt() != null && now.isAfter(q.getExpiresAt())) {
                    bookingVehicleRepository.deleteByBookingID_Id(booking.getId());
                    bookingAssignmentRepository.deleteByBookingID_Id(booking.getId());

                    q.setStatus("EXPIRED");
                    quotationRepository.save(q);

                    booking.setStatus("quote_expired");
                    BookingDetailsPayload payload = extractPayload(booking);
                    appendHistory(payload, "Hệ thống", "Báo giá hết hạn 48 giờ, tự động giải phóng xe và kíp xe.");
                    savePayload(booking, payload);
                    bookingRepository.save(booking);

                    logAction("QUOTE_AUTO_EXPIRED_RELEASE_FLEET", booking.getId(),
                            "Báo giá đơn BK-" + booking.getId()
                                    + " hết hạn 48 giờ. Đã tự động giải phóng xe và kíp xe.");
                    count++;
                }
            }
        }

        if (count > 0) {
            log.info("Released {} expired bookings from awaiting_payment", count);
        }
        return count;
    }

    @Override
    @Transactional
    public int markOverdueSettlements() {
        List<Booking> issuedBookings = bookingRepository.findByStatusOrderByBookingDateAsc("settlement_issued");
        int count = 0;
        long now = System.currentTimeMillis();

        for (Booking booking : issuedBookings) {
            BookingDetailsPayload payload = extractPayload(booking);
            if (payload != null && payload.getSettlement() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> settMap = (Map<String, Object>) payload.getSettlement();
                Object dueAtObj = settMap.get("dueAt");
                if (dueAtObj instanceof Number) {
                    long dueAt = ((Number) dueAtObj).longValue();
                    if (now > dueAt) {
                        booking.setStatus("payment_overdue");
                        appendHistory(payload, "Hệ thống", "Bảng quyết toán quá hạn 24 giờ. Trạng thái chuyển sang payment_overdue (Khóa quyền tạo đơn mới).");
                        savePayload(booking, payload);
                        bookingRepository.save(booking);
                        logAction("SETTLEMENT_OVERDUE", booking.getId(), "Đơn BK-" + booking.getId() + " quá hạn thanh toán quyết toán 24h.");
                        count++;
                    }
                }
            }
        }
        if (count > 0) {
            log.info("Marked {} bookings as payment_overdue", count);
        }
        return count;
    }

    private Integer parseBookingId(String bookingIdentifier) {
        if (bookingIdentifier == null || bookingIdentifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã đơn hàng không được để trống!");
        }
        String cleanId = bookingIdentifier.trim();
        try {
            if (cleanId.toUpperCase().startsWith("BK-")) {
                return Integer.parseInt(cleanId.substring(3).trim());
            } else if (cleanId.toUpperCase().startsWith("ORD-")) {
                String[] parts = cleanId.split("-");
                return Integer.parseInt(parts[parts.length - 1].trim());
            } else {
                return Integer.parseInt(cleanId);
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("Định dạng mã đơn không hợp lệ: " + bookingIdentifier);
        }
    }

    private BookingDetailsPayload extractPayload(Booking booking) {
        BookingDetailsPayload payload = new BookingDetailsPayload();
        if (booking.getDetailsJson() != null && !booking.getDetailsJson().trim().isEmpty()) {
            String json = booking.getDetailsJson().trim();
            try {
                if (json.startsWith("{")) {
                    payload = objectMapper.readValue(json, BookingDetailsPayload.class);
                } else if (json.startsWith("[")) {
                    List<BookingHorseDto> horses = objectMapper.readValue(json,
                            new TypeReference<List<BookingHorseDto>>() {
                            });
                    payload.setHorses(horses);
                }
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Không thể đọc chi tiết đơn " + booking.getId() + ".", ex);
            }
        }
        if (payload.getMedical() == null) {
            Map<String, Object> med = new HashMap<>();
            med.put("status", "pending");
            payload.setMedical(med);
        }
        return payload;
    }

    private String getMedicalStatus(BookingDetailsPayload payload) {
        if (payload == null || payload.getMedical() == null) {
            return "";
        }
        if (payload.getMedical() instanceof Map<?, ?> map) {
            Object status = map.get("status");
            return status != null ? String.valueOf(status) : "";
        }
        return "";
    }

    private void savePayload(Booking booking, BookingDetailsPayload payload) {
        try {
            booking.setDetailsJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không thể lưu chi tiết đơn " + booking.getId() + ".", ex);
        }
    }

    private void appendHistory(BookingDetailsPayload payload, String actor, String text) {
        if (payload.getHistory() == null) {
            payload.setHistory(new ArrayList<>());
        }
        Map<String, Object> entry = new HashMap<>();
        entry.put("time", System.currentTimeMillis());
        entry.put("actor", actor != null ? actor : "Hệ thống");
        entry.put("text", text);
        payload.getHistory().add(entry);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void logAction(String actionType, Integer targetRecordId, String newData) {
        SystemLog sysLog = SystemLog.builder()
                .actionType(actionType)
                .targetTable("BOOKING")
                .targetRecordID(targetRecordId)
                .newData(newData)
                .createdAt(Instant.now())
                .build();
        systemLogRepository.save(sysLog);
    }

    private BookingResponse mapToResponse(Booking b) {
        String formattedId = "BK-" + String.format("%03d", b.getId());
        String customerUsername = "";
        String customerFullName = "";

        if (b.getCustomerID() != null) {
            customerFullName = b.getCustomerID().getFullName() != null ? b.getCustomerID().getFullName() : "";
            if (b.getCustomerID().getUser() != null) {
                customerUsername = b.getCustomerID().getUser().getUsername();
            }
        }

        BookingDetailsPayload payload = null;
        List<BookingHorseDto> horseDtos = new ArrayList<>();
        if (b.getDetailsJson() != null && !b.getDetailsJson().trim().isEmpty()) {
            String json = b.getDetailsJson().trim();
            try {
                if (json.startsWith("{")) {
                    payload = objectMapper.readValue(json, BookingDetailsPayload.class);
                    if (payload.getHorses() != null) {
                        horseDtos = payload.getHorses();
                    }
                } else if (json.startsWith("[")) {
                    horseDtos = objectMapper.readValue(json, new TypeReference<List<BookingHorseDto>>() {
                    });
                }
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Không thể đọc chi tiết đơn " + b.getId() + ".", ex);
            }
        }

        Object medical = payload != null ? payload.getMedical() : null;
        if (medical == null) {
            Map<String, Object> defMed = new HashMap<>();
            defMed.put("status", "pending");
            medical = defMed;
        }

        return BookingResponse.builder()
                .id(formattedId)
                .numericId(b.getId())
                .type(b.getBookingType() != null ? b.getBookingType() : "domestic")
                .customer(customerUsername)
                .customerName(customerFullName)
                .status(b.getStatus() != null ? b.getStatus() : "pending_intake")
                .createdAt(b.getBookingDate() != null ? b.getBookingDate().toEpochMilli() : System.currentTimeMillis())
                .departAt(
                        b.getDepartureDate() != null ? b.getDepartureDate().toEpochMilli() : System.currentTimeMillis())
                .originName(b.getPickupAddress())
                .destName(b.getDropoffAddress())
                .consignor(PartyContactDto.consignorOf(b))
                .consignee(PartyContactDto.consigneeOf(b))
                .totalHorses(b.getTotalHorses() != null ? b.getTotalHorses() : 1)
                .horses(horseDtos)
                .waybillNo(b.getWaybillNo())
                .trips(payload != null ? payload.getTrips() : null)
                .route(payload != null ? payload.getRoute() : null)
                .gate(payload != null ? payload.getGate() : null)
                .plan(payload != null ? payload.getPlan() : null)
                .quote(payload != null ? payload.getQuote() : null)
                .payment(payload != null ? payload.getPayment() : null)
                .medical(medical)
                .clearance(payload != null ? payload.getClearance() : null)
                .settlement(payload != null ? payload.getSettlement() : null)
                .rating(payload != null ? payload.getRating() : null)
                .intake(payload != null ? payload.getIntake() : null)
                .rejection(payload != null ? payload.getRejection() : null)
                .history(payload != null ? payload.getHistory() : null)
                .build();
    }

    // Dev 4: Bàn giao, ký nhận, kết thúc đơn hàng (Flow 6)
    @Override
    @Transactional
    public boolean completeBookingDelivery(Integer bookingId, String recipientName, String recipientSignature,
            String note) {
        // 1. Tìm đơn hàng cần hoàn thành
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng có ID: " + bookingId));

        // 2. Kiểm tra trạng thái hiện tại
        if ("COMPLETED".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalArgumentException("Đơn hàng này đã được bàn giao và hoàn tất trước đó!");
        }

        // 3. Cập nhật trạng thái đơn hàng sang "COMPLETED" (Hoàn thành)
        booking.setStatus("COMPLETED");
        bookingRepository.save(booking);

        // 4. Lưu biên bản bàn giao & chữ ký vào bảng LOG hệ thống để đối soát
        Log deliveryLog = new Log();
        deliveryLog.setId((int) (logRepository.count() + 1));
        deliveryLog.setBookingID(booking);
        deliveryLog.setLogType("DELIVERY_COMPLETED");
        deliveryLog.setStatus("SUCCESS");
        deliveryLog.setNote("Hoàn tất bàn giao ngựa. Người nhận: " + recipientName
                + (recipientSignature != null ? " | Chữ ký/Biên bản: " + recipientSignature : "")
                + (note != null ? " | Ghi chú: " + note : ""));
        deliveryLog.setCreatedAt(Instant.now());
        logRepository.save(deliveryLog);

        return true;
    }

    @Override
    @Transactional
    public BookingResponse updateTripProgress(String bookingIdentifier, com.example.racehorse_transport.dto.field.TripUpdateRequest request) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + bookingIdentifier));

        BookingDetailsPayload payload = extractPayload(booking);
        List<com.example.racehorse_transport.dto.coordinator.VehicleTripDto> trips = payload.getTrips();
        if (trips == null || trips.isEmpty()) {
            throw new IllegalStateException("Đơn hàng chưa có thông tin chuyến xe!");
        }

        String tripId = request.getTripId();
        com.example.racehorse_transport.dto.coordinator.VehicleTripDto targetTrip = trips.stream()
                .filter(t -> t.getTripId() != null && t.getTripId().equalsIgnoreCase(tripId))
                .findFirst()
                .orElse(trips.get(0));

        String action = request.getAction() != null ? request.getAction() : "";
        String actor = request.getActor() != null ? request.getActor() : "Kíp xe";
        Map<String, Object> reqPayload = request.getPayload() != null ? request.getPayload() : new HashMap<>();
        long now = System.currentTimeMillis();

        switch (action.toUpperCase()) {
            case "ACKNOWLEDGE":
            case "ACK": {
                String who = String.valueOf(reqPayload.getOrDefault("who", "driver"));
                Map<String, Object> acks = targetTrip.getAcks() != null ? targetTrip.getAcks() : new HashMap<>();
                acks.put(who, now);
                targetTrip.setAcks(acks);
                appendHistory(payload, actor, actor + " (" + ("driver".equalsIgnoreCase(who) ? "Tài xế" : "Hộ tống") + ") đã nhận Lệnh điều xe");

                // Kiểm tra xem kíp xe (Driver và Escort) của các chuyến đã xác nhận đủ chưa
                boolean allTripsAcked = trips.stream().allMatch(t -> {
                    Map<String, Object> a = t.getAcks();
                    return a != null && a.get("driver") != null && a.get("escort") != null;
                });

                if (allTripsAcked) {
                    // Lấy tên Specialist được Quản lý phân công từ lúc tiếp nhận đơn
                    String specialistName = "Chuyên viên kiểm dịch";
                    if (payload.getIntake() instanceof Map<?, ?> intakeMap && intakeMap.get("specialist") instanceof Map<?, ?> spMap) {
                        if (spMap.get("name") != null) {
                            specialistName = String.valueOf(spMap.get("name"));
                        }
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> clearanceMap = payload.getClearance() instanceof Map<?, ?> existingClr
                            ? new HashMap<>((Map<String, Object>) existingClr)
                            : new HashMap<>();

                    clearanceMap.put("crewAckedAt", now);

                    if (clearanceMap.get("doneAt") != null) {
                        booking.setStatus("ready_for_pickup");
                        appendHistory(payload, "Hệ thống", "Kíp xe (Tài xế & Hộ tống) đã nhận lệnh điều xe. Giấy tờ đã hoàn tất trước đó, đơn hàng chuyển sang trạng thái Sẵn sàng đón ngựa!");
                        log.info("Booking ID={} advanced to ready_for_pickup because clearance was already done", booking.getId());
                    } else {
                        booking.setStatus("clearance_in_progress");
                        clearanceMap.put("specialistName", specialistName);
                        clearanceMap.put("quarantineStatus", "PENDING_SPECIALIST_REVIEW");
                        appendHistory(payload, "Hệ thống", "Kíp xe (Tài xế & Hộ tống) đã xác nhận đủ giấy tờ chuyến đi. Đơn được tự động chuyển sang Chuyên viên kiểm dịch (" + specialistName + ") để duyệt xác nhận dịch tễ.");
                        log.info("Booking ID={} automatically transferred to Specialist [{}] for quarantine review after crew acknowledged", booking.getId(), specialistName);
                    }
                    payload.setClearance(clearanceMap);
                }
                break;
            }
            case "SET_DRIVER_PACK": {
                @SuppressWarnings("unchecked")
                List<String> items = reqPayload.get("items") instanceof List<?> list
                        ? (List<String>) list
                        : new ArrayList<>();
                Map<String, Object> pack = new HashMap<>();
                pack.put("items", items);
                pack.put("at", now);
                pack.put("by", actor);
                targetTrip.setDriverPack(pack);
                appendHistory(payload, actor, "Điều phối viên (" + actor + ") đã lập bộ " + items.size() + " giấy tờ bàn giao cho tài xế");
                break;
            }
            case "DEPART_TO_PICKUP": {
                targetTrip.setDepartedAt(now);
                booking.setStatus("en_route_to_pickup");
                appendHistory(payload, actor, "Xe bắt đầu xuất phát đến điểm đón ngựa");
                break;
            }
            case "ARRIVE_PICKUP": {
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) run.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                if (checkpoints.isEmpty()) {
                    Map<String, Object> cp = new HashMap<>();
                    cp.put("id", "pickup");
                    cp.put("type", "pickup");
                    cp.put("arrivedAt", now);
                    cp.put("photo", reqPayload.get("photo"));
                    checkpoints.add(cp);
                } else {
                    checkpoints.get(0).put("arrivedAt", now);
                    checkpoints.get(0).put("photo", reqPayload.get("photo"));
                }
                targetTrip.setRun(run);
                appendHistory(payload, actor, "Tài xế đã có mặt tại điểm đón");
                break;
            }
            case "SCAN_CHIP": {
                String chip = String.valueOf(reqPayload.getOrDefault("chip", "")).toUpperCase().trim();
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) run.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                if (checkpoints.isEmpty()) {
                    Map<String, Object> cp = new HashMap<>();
                    cp.put("id", "pickup");
                    checkpoints.add(cp);
                }
                Map<String, Object> pickup = checkpoints.get(0);
                @SuppressWarnings("unchecked")
                List<String> chips = (List<String>) pickup.computeIfAbsent("chips", k -> new ArrayList<>());
                if (!chips.contains(chip) && !chip.isEmpty()) {
                    chips.add(chip);
                }
                targetTrip.setRun(run);
                appendHistory(payload, actor, "Hộ tống quét microchip: " + chip);
                break;
            }
            case "COLLECT_ORIGINALS": {
                Object items = reqPayload.get("items");
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) run.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                if (checkpoints.isEmpty()) {
                    Map<String, Object> cp = new HashMap<>();
                    cp.put("id", "pickup");
                    checkpoints.add(cp);
                }
                checkpoints.get(0).put("originals", items);
                targetTrip.setRun(run);
                appendHistory(payload, actor, "Tài xế đã thu đủ hồ sơ gốc");
                break;
            }
            case "UPLOAD_HANDOVER": {
                String photo = String.valueOf(reqPayload.getOrDefault("photo", ""));
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) run.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                if (!checkpoints.isEmpty()) {
                    checkpoints.get(0).put("handoverPhoto", photo);
                }
                targetTrip.setRun(run);
                appendHistory(payload, actor, "Tải ảnh biên bản giao nhận có chữ ký");
                break;
            }
            case "START_JOURNEY": {
                booking.setStatus("in_transit");
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                run.put("startedAt", now);
                targetTrip.setRun(run);
                appendHistory(payload, actor, "Xe bắt đầu hành trình vận chuyển ngựa");
                break;
            }
            case "ARRIVE_CHECKPOINT": {
                String cpId = String.valueOf(reqPayload.getOrDefault("checkpointId", ""));
                String photo = String.valueOf(reqPayload.getOrDefault("photo", ""));
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) run.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                for (Map<String, Object> cp : checkpoints) {
                    if (cpId.equals(cp.get("id"))) {
                        cp.put("arrivedAt", now);
                        cp.put("photo", photo);
                    }
                }
                targetTrip.setRun(run);
                appendHistory(payload, actor, "Xe tới trạm dừng / mốc: " + cpId);
                break;
            }
            case "SUBMIT_WELFARE": {
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> welfareList = (List<Map<String, Object>>) run.computeIfAbsent("welfare", k -> new ArrayList<>());
                Map<String, Object> w = new HashMap<>(reqPayload);
                w.put("at", now);
                w.put("by", actor);
                welfareList.add(w);
                targetTrip.setRun(run);
                appendHistory(payload, actor, "Hộ tống gửi nhật ký an sinh ngựa");
                break;
            }
            case "CONTINUE_JOURNEY": {
                appendHistory(payload, actor, "Xe tiếp tục hành trình");
                break;
            }
            case "CUSTOMS_CLEARED": {
                appendHistory(payload, actor, "Đã hoàn tất thủ tục thông quan tại cửa khẩu");
                break;
            }
            case "COMPLETE_DELIVERY": {
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                run.put("deliveredAt", now);
                targetTrip.setRun(run);

                Map<String, Object> hr = new HashMap<>();
                hr.put("fromPlace", booking.getDropoffAddress() != null ? booking.getDropoffAddress() : "Điểm giao");
                hr.put("earliestAt", now + 1000);
                hr.put("eta", now + 7200000L);
                targetTrip.setHomeReturn(hr);

                booking.setStatus("delivered_pending_settlement");
                appendHistory(payload, actor, "Hoàn tất bàn giao ngựa, đơn chuyển sang chờ đối soát chi phí");
                break;
            }
            case "CONFIRM_HOME": {
                Map<String, Object> hr = targetTrip.getHomeReturn() != null ? targetTrip.getHomeReturn() : new HashMap<>();
                hr.put("backAt", now);
                targetTrip.setHomeReturn(hr);

                if (targetTrip.getVehicleId() != null) {
                    try {
                        Integer vId = Integer.parseInt(targetTrip.getVehicleId().replaceAll("[^0-9]", ""));
                        vehicleRepository.findById(vId).ifPresent(v -> {
                            v.setStatus("AVAILABLE");
                            vehicleRepository.save(v);
                        });
                    } catch (Exception ignored) {}
                }
                appendHistory(payload, actor, "Xe và kíp xe đã về tới Trụ sở chính Việt Nam, sẵn sàng nhận chuyến mới");
                break;
            }
            default: {
                log.warn("Unknown trip action: {}", action);
            }
        }

        savePayload(booking, payload);
        Booking updated = bookingRepository.save(booking);
        logAction("TRIP_UPDATE_" + action, updated.getId(), actor + " thực hiện " + action + " cho chuyến " + tripId);
        return mapToResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicTrackingResponse getPublicTracking(String code, String phoneLast4) {
        if (code == null || code.isBlank() || phoneLast4 == null || phoneLast4.isBlank()) {
            return null;
        }

        String trimmedCode = code.trim();
        String cleanPhoneLast4 = phoneLast4.replaceAll("\\D", "").trim();
        if (cleanPhoneLast4.length() < 4) {
            return null;
        }
        cleanPhoneLast4 = cleanPhoneLast4.substring(cleanPhoneLast4.length() - 4);

        // 1. Tìm booking theo ID hoặc WaybillNo
        Optional<Booking> bookingOpt = Optional.empty();
        String digitsOnly = trimmedCode.replaceAll("\\D", "");
        if (!digitsOnly.isBlank()) {
            try {
                bookingOpt = bookingRepository.findById(Integer.parseInt(digitsOnly));
            } catch (Exception ignored) {}
        }
        if (bookingOpt.isEmpty()) {
            bookingOpt = bookingRepository.findByWaybillNoIgnoreCase(trimmedCode);
        }
        if (bookingOpt.isEmpty()) {
            return null;
        }

        Booking booking = bookingOpt.get();

        // 2. Xác thực 4 số cuối số điện thoại (Consignor, Consignee, hoặc Customer Phone)
        boolean phoneMatched = false;
        if (checkPhoneLast4(booking.getConsignorPhone(), cleanPhoneLast4)
                || checkPhoneLast4(booking.getConsigneePhone(), cleanPhoneLast4)) {
            phoneMatched = true;
        } else if (booking.getCustomerID() != null) {
            if (checkPhoneLast4(booking.getCustomerID().getPhone(), cleanPhoneLast4)) {
                phoneMatched = true;
            }
        }

        if (!phoneMatched) {
            return null;
        }

        // 3. Map dữ liệu sang PublicTrackingResponse
        String origin = booking.getPickupAddress() != null && !booking.getPickupAddress().isBlank()
                ? booking.getPickupAddress().split(" — ")[0]
                : "Điểm đón";
        String dest = booking.getDropoffAddress() != null && !booking.getDropoffAddress().isBlank()
                ? booking.getDropoffAddress().split(" — ")[0]
                : "Điểm giao";
        String route = origin + " → " + dest;

        String depart = booking.getDepartureDate() != null ? booking.getDepartureDate().toString() : null;
        String status = booking.getStatus() != null ? booking.getStatus() : "pending_intake";

        int step = resolvePublicStep(status);
        boolean done = "completed".equalsIgnoreCase(status);
        String statusLabel = resolvePublicCustomerStatus(status);
        String tone = resolvePublicTone(status);

        BookingDetailsPayload payload = extractPayload(booking);
        Map<String, Object> nowMap = null;
        if (payload != null && payload.getTrips() != null && !payload.getTrips().isEmpty()) {
            VehicleTripDto trip = payload.getTrips().get(0);
            if (trip.getRun() != null) {
                Object cpObj = trip.getRun().get("checkpoints");
                if (cpObj instanceof List<?> list && !list.isEmpty()) {
                    Object last = list.get(list.size() - 1);
                    if (last instanceof Map<?, ?> rawMap) {
                        nowMap = new HashMap<>();
                        Object placeVal = rawMap.get("name");
                        Object atVal = rawMap.get("at");
                        Object etaVal = rawMap.get("eta");
                        nowMap.put("place", placeVal != null ? placeVal.toString() : "Đang trên hành trình");
                        nowMap.put("at", atVal != null ? atVal : System.currentTimeMillis());
                        nowMap.put("eta", etaVal != null ? etaVal : System.currentTimeMillis() + 3600000L);
                    }
                }
            }
        }

        return PublicTrackingResponse.builder()
                .route(route)
                .depart(depart)
                .step(step)
                .done(done)
                .status(statusLabel)
                .tone(tone)
                .now(nowMap)
                .note(null)
                .build();
    }

    private boolean checkPhoneLast4(String fullPhone, String last4) {
        if (fullPhone == null || fullPhone.isBlank() || last4 == null || last4.isBlank()) {
            return false;
        }
        String digits = fullPhone.replaceAll("\\D", "");
        return digits.endsWith(last4);
    }

    private int resolvePublicStep(String status) {
        if (status == null) return 0;
        return switch (status.toLowerCase()) {
            case "pending_intake", "cancelled", "rejected" -> 0;
            case "under_review", "pending_commercial" -> 1;
            case "awaiting_payment", "quote_expired" -> 2;
            case "waybill_issued", "clearance_in_progress", "clearance_done", "ready_for_pickup", "en_route_to_pickup" -> 3;
            case "in_transit", "incident_reported", "pending_emergency_approval", "emergency_plan_active",
                 "delivered_pending_settlement", "expenses_submitted", "settlement_issued", "payment_overdue", "completed" -> 4;
            default -> 0;
        };
    }

    private String resolvePublicCustomerStatus(String status) {
        if (status == null) return "Đang xử lý";
        return switch (status.toLowerCase()) {
            case "pending_intake" -> "Đã gửi, chờ tiếp nhận";
            case "under_review" -> "Đang thẩm định";
            case "pending_commercial" -> "Đang lập báo giá";
            case "awaiting_payment" -> "Chờ đặt cọc";
            case "quote_expired" -> "Báo giá hết hạn";
            case "waybill_issued" -> "Đã có vận đơn";
            case "clearance_in_progress" -> "Đang làm thủ tục giấy tờ";
            case "clearance_done" -> "Giấy tờ đã xong";
            case "ready_for_pickup" -> "Sẵn sàng đón ngựa";
            case "en_route_to_pickup" -> "Xe đang đến điểm đón";
            case "in_transit" -> "Đang vận chuyển";
            case "incident_reported", "pending_emergency_approval" -> "Xe gặp sự cố, đang xử lý";
            case "emergency_plan_active" -> "Đang xử lý sự cố, ngựa được chăm sóc";
            case "delivered_pending_settlement" -> "Đã giao ngựa";
            case "expenses_submitted" -> "Đã giao, đang quyết toán";
            case "settlement_issued" -> "Chờ thanh toán quyết toán";
            case "payment_overdue" -> "Quá hạn thanh toán";
            case "completed" -> "Đã hoàn tất";
            case "cancelled" -> "Đã hủy";
            case "rejected" -> "Đơn bị từ chối";
            default -> status;
        };
    }

    private String resolvePublicTone(String status) {
        if (status == null) return null;
        return switch (status.toLowerCase()) {
            case "awaiting_payment", "settlement_issued", "emergency_plan_active" -> "warn";
            case "incident_reported", "pending_emergency_approval", "payment_overdue", "rejected" -> "bad";
            case "completed", "waybill_issued", "clearance_done", "delivered_pending_settlement" -> "done";
            default -> null;
        };
    }
}
