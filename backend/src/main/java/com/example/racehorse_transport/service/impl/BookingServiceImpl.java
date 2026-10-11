package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.booking.CreateBookingRequest;
import com.example.racehorse_transport.dto.booking.PartyContactDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.BookingService;
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
    private final StaffRepository staffRepository;
    private final QuotationRepository quotationRepository;
    private final PaymentRepository paymentRepository;
    private final FleetService fleetService;
    private final LogRepository logRepository;
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
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin người dùng: " + customerUsername));

        // 2. Tìm hoặc tự động tạo Customer profile
        Customer customer = customerRepository.findById(user.getId())
                .orElseGet(() -> {
                    Customer c = new Customer();
                    c.setUser(user);
                    c.setFullName(request.getConsignor() != null ? request.getConsignor().getName() : user.getUsername());
                    c.setPhone(request.getConsignor() != null ? request.getConsignor().getPhone() : "");
                    return customerRepository.save(c);
                });

        // 3. Chuyển đổi timestamp ngày khởi hành
        Instant departureInstant = request.getDepartAt() != null
                ? Instant.ofEpochMilli(request.getDepartAt())
                : Instant.now().plusSeconds(86400 * 3);

        // 4. Serialize cấu hình chi tiết ngựa vào detailsJson
        String detailsJson = null;
        try {
            detailsJson = objectMapper.writeValueAsString(request.getHorses());
        } catch (Exception e) {
            log.warn("Could not serialize horses detailsJson: {}", e.getMessage());
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
                .pickupAddress(request.getOriginName() != null ? request.getOriginName().trim() : "")
                .dropoffAddress(request.getDestName() != null ? request.getDestName().trim() : "")
                .totalHorses(request.getHorses() != null ? request.getHorses().size() : 1)
                .detailsJson(detailsJson)
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        // 6. Lưu danh sách ngựa cơ bản của khách hàng
        if (request.getHorses() != null) {
            for (BookingHorseDto hDto : request.getHorses()) {
                try {
                    Horse horse = Horse.builder()
                            .customerID(customer)
                            .horseName(hDto.getName() != null ? hDto.getName().trim() : "Ngựa chưa đặt tên")
                            .microchipID(hDto.getMicrochip() != null ? hDto.getMicrochip().trim() : null)
                            .gender(hDto.getSex() != null ? hDto.getSex().trim() : "stallion")
                            .status("ACTIVE")
                            .build();
                    horseRepository.save(horse);
                } catch (Exception ex) {
                    log.warn("Could not save horse: {}", ex.getMessage());
                }
            }
        }

        // 7. Ghi Audit Log vào SYSTEM_LOG
        try {
            SystemLog sysLog = SystemLog.builder()
                    .actionbyUserid(user)
                    .actionType("CREATE_BOOKING")
                    .targetTable("BOOKING")
                    .targetRecordID(savedBooking.getId())
                    .newData("Tạo đơn vận chuyển " + savedBooking.getTotalHorses() + " con ngựa (" + savedBooking.getPickupAddress() + " -> " + savedBooking.getDropoffAddress() + ")")
                    .createdAt(Instant.now())
                    .build();
            systemLogRepository.save(sysLog);
        } catch (Exception ignored) {
        }

        log.info("Successfully created booking ID={} for user={}", savedBooking.getId(), customerUsername);
        return mapToResponse(savedBooking);
    }

    @Override
    public List<BookingResponse> getCustomerBookings(String customerUsername) {
        User user = userRepository.findByUsernameOrEmail(customerUsername, customerUsername)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin người dùng: " + customerUsername));

        return bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(user.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public BookingResponse getBookingById(Integer id, String customerUsername) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng có ID: " + id));

        return mapToResponse(booking);
    }

    @Override
    public List<BookingResponse> getBookingsByStatus(String status) {
        if (status == null || status.trim().isEmpty() || "ALL".equalsIgnoreCase(status)) {
            return bookingRepository.findAllByOrderByBookingDateDesc()
                    .stream()
                    .map(this::mapToResponse)
                    .toList();
        }
        return bookingRepository.findByStatusOrderByBookingDateAsc(status)
                .stream()
                .map(this::mapToResponse)
                .toList();
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
        } catch (Exception ex) {
            throw new IllegalArgumentException("Định dạng mã đơn không hợp lệ: " + bookingIdentifier);
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
                throw new IllegalArgumentException("Xe " + vehicle.getLicensePlate() + " chỉ có " + vehicle.getCapacity()
                        + " ngăn, không thể chở " + trip.getHorseIds().size() + " con ngựa!");
            }

            // Kiểm tra lịch bận & khứ hồi về trụ sở VN
            fleetService.validateVehicleAvailability(vehicle.getId(), departDate, booking.getBookingType(), booking.getId());

            // Chuẩn hóa tripId
            if (trip.getTripId() == null || trip.getTripId().trim().isEmpty()) {
                trip.setTripId("TRP-" + String.format("%03d", booking.getId()) + "-" + (i + 1));
            }
            // Gán vehicleId là id chuẩn dạng số
            trip.setVehicleId(String.valueOf(vehicle.getId()));

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
                    List<BookingHorseDto> horses = objectMapper.readValue(existingJson, new TypeReference<List<BookingHorseDto>>() {});
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

        try {
            booking.setDetailsJson(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            log.error("Could not serialize payload: {}", ex.getMessage());
        }

        // 6. Cập nhật trạng thái đơn sang pending_commercial
        booking.setStatus("pending_commercial");
        Booking updatedBooking = bookingRepository.save(booking);

        // 7. Ghi nhật ký hệ thống SYSTEM_LOG
        try {
            SystemLog sysLog = SystemLog.builder()
                    .actionType("COORDINATOR_CONFIRM_PLAN")
                    .targetTable("BOOKING")
                    .targetRecordID(updatedBooking.getId())
                    .newData("Điều phối viên (" + (request.getBy() != null ? request.getBy() : "coordinator") + ") chốt "
                            + request.getTrips().size() + " xe và lộ trình đơn BK-" + updatedBooking.getId()
                            + ", chuyển quản lý duyệt báo giá & phân tài/hộ tống.")
                    .createdAt(Instant.now())
                    .build();
            systemLogRepository.save(sysLog);
        } catch (Exception ignored) {}

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
            throw new IllegalStateException("Chỉ phân công kíp xe khi đơn đang ở bước duyệt báo giá (pending_commercial)!");
        }

        if (request.getPicks() == null || request.getPicks().isEmpty()) {
            throw new IllegalArgumentException("Danh sách phân công kíp xe không được để trống!");
        }

        BookingDetailsPayload payload = extractPayload(booking);
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
                throw new IllegalArgumentException("Mỗi xe cần một tài xế riêng biệt, không thể chọn 1 tài xế cho 2 xe!");
            }
            if (!usedEscorts.add(pick.getEscortId().trim())) {
                throw new IllegalArgumentException("Mỗi xe cần một hộ tống riêng biệt, không thể chọn 1 hộ tống cho 2 xe!");
            }

            Staff driver = fleetService.findStaffByIdOrCode(pick.getDriverId(), "DRIVER");
            fleetService.validateCrewAvailability(driver.getId(), departDate, booking.getBookingType(), booking.getId());

            Staff escort = fleetService.findStaffByIdOrCode(pick.getEscortId(), "ESCORT");
            fleetService.validateCrewAvailability(escort.getId(), departDate, booking.getBookingType(), booking.getId());

            int idx = i;
            VehicleTripDto matchedTrip = trips.stream()
                    .filter(t -> t.getTripId() != null && t.getTripId().equalsIgnoreCase(pick.getTripId()))
                    .findFirst()
                    .orElse(trips.get(idx));

            matchedTrip.setDriverId(driver.getStaffCode() != null ? driver.getStaffCode() : String.valueOf(driver.getId()));
            matchedTrip.setEscortId(escort.getStaffCode() != null ? escort.getStaffCode() : String.valueOf(escort.getId()));

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
        List<VehicleTripDto> trips = payload.getTrips();
        if (trips == null || trips.isEmpty()) {
            throw new IllegalStateException("Đơn chưa có phương án xe để gửi báo giá!");
        }
        for (VehicleTripDto t : trips) {
            if (t.getDriverId() == null || t.getDriverId().isBlank() || t.getEscortId() == null || t.getEscortId().isBlank()) {
                throw new IllegalStateException("Cần phân công tài xế và hộ tống cho tất cả các xe trước khi duyệt báo giá!");
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
                .adjustmentNote(request.getAdjustments() != null && !request.getAdjustments().isEmpty() ? request.getAdjustments().get(0).getLabel() : null)
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
        logAction("SEND_BACK", updated.getId(), "Quản lý trả đơn BK-" + updated.getId() + " về " + request.getTo() + ": " + request.getReason());
        log.info("Booking ID={} sent back to {}", updated.getId(), request.getTo());
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public BookingResponse payDeposit(String bookingIdentifier, String customerUsername, com.example.racehorse_transport.dto.booking.PayDepositRequest request) {
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

        logAction("PAY_DEPOSIT", updated.getId(), "Khách hàng thanh toán cọc 30% (" + payAmount + " VND), cấp Vận đơn " + waybillNo);
        log.info("Successfully paid deposit for booking ID={}, waybill={}", updated.getId(), waybillNo);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public BookingResponse rejectQuote(String bookingIdentifier, String customerUsername, com.example.racehorse_transport.dto.booking.RejectQuoteRequest request) {
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

        String reason = request != null && request.getReason() != null ? request.getReason().trim() : "Khách hàng không đồng ý báo giá";
        appendHistory(payload, customerUsername != null ? customerUsername : "Khách hàng",
                "Từ chối báo giá: " + reason + ". Đơn đã hủy, giải phóng xe và kíp xe.");
        savePayload(booking, payload);

        Booking updated = bookingRepository.save(booking);
        logAction("REJECT_QUOTE", updated.getId(), "Khách hàng từ chối báo giá đơn BK-" + updated.getId() + ": " + reason);
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
                            "Báo giá đơn BK-" + booking.getId() + " hết hạn 48 giờ. Đã tự động giải phóng xe và kíp xe.");
                    count++;
                }
            }
        }

        if (count > 0) {
            log.info("Released {} expired bookings from awaiting_payment", count);
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
                    List<BookingHorseDto> horses = objectMapper.readValue(json, new TypeReference<List<BookingHorseDto>>() {});
                    payload.setHorses(horses);
                }
            } catch (Exception ex) {
                log.warn("Error reading detailsJson: {}", ex.getMessage());
            }
        }
        return payload;
    }

    private void savePayload(Booking booking, BookingDetailsPayload payload) {
        try {
            booking.setDetailsJson(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            log.error("Could not serialize payload: {}", ex.getMessage());
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

    private void logAction(String actionType, Integer targetRecordId, String newData) {
        try {
            SystemLog sysLog = SystemLog.builder()
                    .actionType(actionType)
                    .targetTable("BOOKING")
                    .targetRecordID(targetRecordId)
                    .newData(newData)
                    .createdAt(Instant.now())
                    .build();
            systemLogRepository.save(sysLog);
        } catch (Exception ignored) {}
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
                    horseDtos = objectMapper.readValue(json, new TypeReference<List<BookingHorseDto>>() {});
                }
            } catch (Exception ignored) {
            }
        }

        return BookingResponse.builder()
                .id(formattedId)
                .numericId(b.getId())
                .type(b.getBookingType() != null ? b.getBookingType() : "domestic")
                .customer(customerUsername)
                .customerName(customerFullName)
                .status(b.getStatus() != null ? b.getStatus() : "pending_intake")
                .createdAt(b.getBookingDate() != null ? b.getBookingDate().toEpochMilli() : System.currentTimeMillis())
                .departAt(b.getDepartureDate() != null ? b.getDepartureDate().toEpochMilli() : System.currentTimeMillis())
                .originName(b.getPickupAddress())
                .destName(b.getDropoffAddress())
                .consignor(PartyContactDto.builder().name(b.getConsignorName()).phone(b.getConsignorPhone()).build())
                .consignee(PartyContactDto.builder().name(b.getConsigneeName()).phone(b.getConsigneePhone()).build())
                .totalHorses(b.getTotalHorses() != null ? b.getTotalHorses() : 1)
                .horses(horseDtos)
                .waybillNo(b.getWaybillNo())
                .trips(payload != null ? payload.getTrips() : null)
                .route(payload != null ? payload.getRoute() : null)
                .gate(payload != null ? payload.getGate() : null)
                .plan(payload != null ? payload.getPlan() : null)
                .quote(payload != null ? payload.getQuote() : null)
                .payment(payload != null ? payload.getPayment() : null)
                .medical(payload != null ? payload.getMedical() : null)
                .intake(payload != null ? payload.getIntake() : null)
                .history(payload != null ? payload.getHistory() : null)
                .build();
    }

    // Dev 4: Bàn giao, ký nhận, kết thúc đơn hàng (Flow 6)
    @Override
    @Transactional
    public boolean completeBookingDelivery(Integer bookingId, String recipientName, String recipientSignature, String note) {
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
}

