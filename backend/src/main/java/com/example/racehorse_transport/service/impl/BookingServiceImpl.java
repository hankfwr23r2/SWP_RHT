package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.dto.booking.DocumentRequirementSnapshot;
import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.booking.BookingServiceSelectionDto;
import com.example.racehorse_transport.dto.booking.CreateBookingRequest;
import com.example.racehorse_transport.dto.booking.CustomerTripTeamResponse;
import com.example.racehorse_transport.dto.booking.PublicTrackingResponse;
import com.example.racehorse_transport.dto.booking.PartyContactDto;
import com.example.racehorse_transport.dto.common.PagedResponse;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.example.racehorse_transport.service.BookingService;
import com.example.racehorse_transport.service.SystemConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.dto.coordinator.CoordinatorPlanRequest;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.dto.manager.*;
import com.example.racehorse_transport.service.FleetService;
import com.example.racehorse_transport.util.BookingDetailsJson;
import com.example.racehorse_transport.util.ClearanceDocumentPolicy;
import com.example.racehorse_transport.util.QuoteTotals;
import com.example.racehorse_transport.util.CustomerBookingRules;
import com.example.racehorse_transport.util.DocumentRequirementPolicy;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.example.racehorse_transport.repository.RouteRepository;
import com.example.racehorse_transport.repository.ServiceRepository;
import com.example.racehorse_transport.entity.Route;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    // Hạn giữ báo giá: quá hạn chưa đặt cọc thì nhả xe và kíp xe
    public static final int QUOTE_VALID_HOURS = 24;

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final HorseRepository horseRepository;
    private final SystemLogRepository systemLogRepository;
    private final BookingVehicleRepository bookingVehicleRepository;
    private final BookingAssignmentRepository bookingAssignmentRepository;
    private final QuotationRepository quotationRepository;
    private final NotificationRepository notificationRepository;
    private final PaymentRepository paymentRepository;
    private final FleetService fleetService;
    private final LogRepository logRepository;
    private final VehicleRepository vehicleRepository;
    private final StaffRepository staffRepository;
    private final RegulationRepository regulationRepository;
    private final LocationRepository locationRepository;
    private final SystemConfigService systemConfigService;
    private final TripDocumentRepository tripDocumentRepository;
    private final RouteRepository routeRepository;
    private final ServiceRepository serviceRepository;
    private final HorseDocumentRepository horseDocumentRepository;
    private final DocumentRequirementRepository documentRequirementRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // =========================================================================
    // PHẦN VIỆC CỦA DEV 1 (Lead): Quản lý Đơn hàng (Flow 1)
    // =========================================================================

    @Override
    @Transactional
    public BookingResponse createBooking(String customerUsername, CreateBookingRequest request) {
        // 1. Tìm thông tin User
        User user = userRepository.findByUsernameOrEmail(customerUsername, customerUsername)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng: " + customerUsername));

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
        int minimumLeadDays = Integer.parseInt(systemConfigService.getConfig("MIN_LEAD_DAYS").getConfigValue());
        ZoneId vietnamZone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate earliestDeparture = LocalDate.now(vietnamZone).plusDays(minimumLeadDays);
        Instant departureInstant = request.getDepartAt() != null
                ? Instant.ofEpochMilli(request.getDepartAt())
                : earliestDeparture.atTime(9, 0).atZone(vietnamZone).toInstant();
        if (departureInstant.atZone(vietnamZone).toLocalDate().isBefore(earliestDeparture)) {
            throw new IllegalArgumentException("Ngày khởi hành phải cách ngày đặt đơn tối thiểu "
                    + minimumLeadDays + " ngày.");
        }

        // 4. Resolve selected services against active catalog data before persisting a price snapshot.
        snapshotSelectedServices(request.getHorses());
        boolean internationalBooking = "INTERNATIONAL".equalsIgnoreCase(request.getType());
        List<DocumentRequirement> bookingDocumentRequirements = documentRequirementRepository
                .findByStatusOrderByDocumentGroupAscDocumentNameAsc("ACTIVE")
                .stream()
                .filter(DocumentRequirement::isRequired)
                .filter(requirement -> DocumentRequirementPolicy.applies(
                        requirement, request.getType(), request.getDestCountry()))
                .filter(requirement -> internationalBooking
                        || !"CLEARANCE".equals(requirement.getDocumentGroup())
                        || !ClearanceDocumentPolicy.isInternationalOnlyType(requirement.getDocumentType()))
                .toList();

        // 5. Serialize cấu hình chi tiết ngựa và khởi tạo payload vào detailsJson
        String detailsJson = null;
        try {
            BookingDetailsPayload initialPayload = new BookingDetailsPayload();
            initialPayload.setOriginCountry(request.getOriginCountry());
            initialPayload.setDestCountry(request.getDestCountry());
            initialPayload.setHorses(request.getHorses() != null ? request.getHorses() : new ArrayList<>());
            initialPayload.setDocumentRequirements(bookingDocumentRequirements.stream()
                    .map(requirement -> DocumentRequirementSnapshot.builder()
                            .documentGroup(requirement.getDocumentGroup())
                            .documentType(requirement.getDocumentType())
                            .documentName(requirement.getDocumentName())
                            .description(requirement.getDescription())
                            .build())
                    .toList());
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

        // 6. Xác nhận các ngựa được chọn:
        // - Phải thuộc tài khoản của khách hàng này
        // - Không được mang trạng thái IN_TRANSIT
        // - Không được đang nằm trong bất kỳ đơn hàng nào chưa hoàn tất (khác completed, cancelled, rejected, quote_expired)
        if (request.getHorses() != null && !request.getHorses().isEmpty()) {
            Set<String> finishedStatuses = Set.of("completed", "cancelled", "rejected", "quote_expired");
            Map<Integer, String> horseToActiveBookingMap = new HashMap<>();
            List<DocumentRequirement> requiredHorseDocuments = bookingDocumentRequirements.stream()
                    .filter(requirement -> "HORSE".equals(requirement.getDocumentGroup()))
                    .toList();

            if (customerBookings != null) {
                for (Booking cb : customerBookings) {
                    String cbStatus = cb.getStatus() != null ? cb.getStatus().toLowerCase() : "";
                    if (!finishedStatuses.contains(cbStatus)) {
                        if (cb.getDetailsJson() != null && !cb.getDetailsJson().isBlank()) {
                            try {
                                BookingDetailsPayload p = objectMapper.readValue(cb.getDetailsJson(), BookingDetailsPayload.class);
                                if (p != null && p.getHorses() != null) {
                                    for (BookingHorseDto activeHorse : p.getHorses()) {
                                        if (activeHorse.getHorseId() != null) {
                                            String num = activeHorse.getHorseId().replaceAll("\\D", "");
                                            if (!num.isEmpty()) {
                                                Integer aId = Integer.valueOf(num);
                                                horseToActiveBookingMap.putIfAbsent(aId, "#BK-" + cb.getId() + " (" + cb.getStatus() + ")");
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("Không thể đọc detailsJson của đơn hàng {}: {}", cb.getId(), e.getMessage());
                            }
                        }
                    }
                }
            }

            for (BookingHorseDto hDto : request.getHorses()) {
                if (hDto.getHorseId() == null || hDto.getHorseId().isBlank()) {
                    throw new IllegalArgumentException("Mỗi ngựa trong đơn phải được chọn từ Hồ sơ ngựa.");
                }
                String numericId = hDto.getHorseId().replaceAll("\\D", "");
                if (numericId.isBlank()) {
                    throw new IllegalArgumentException("Mã hồ sơ ngựa không hợp lệ: " + hDto.getHorseId());
                }
                Integer horseId = Integer.valueOf(numericId);
                Horse horse = horseRepository.findById(horseId)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ ngựa " + hDto.getHorseId()));
                if (horse.getCustomerID() == null || !customer.getId().equals(horse.getCustomerID().getId())) {
                    throw new IllegalArgumentException("Hồ sơ ngựa " + hDto.getHorseId() + " không thuộc tài khoản của bạn.");
                }
                String currentCountry = normalizeCountryCode(horse.getCurrentCountry());
                String originCountry = normalizeCountryCode(request.getOriginCountry());
                if (trimToNull(horse.getCurrentLocationName()) != null
                        && (currentCountry == null || !Set.of("VN", "KH", "LA").contains(currentCountry))) {
                    throw new IllegalStateException("Ngựa \"" + (horse.getHorseName() != null ? horse.getHorseName() : hDto.getHorseId())
                            + "\" chưa xác định quốc gia của vị trí chuồng. Vui lòng cập nhật hồ sơ ngựa trước khi tạo đơn.");
                }
                if (currentCountry != null && originCountry != null && !currentCountry.equals(originCountry)) {
                    throw new IllegalStateException("Ngựa \"" + (horse.getHorseName() != null ? horse.getHorseName() : hDto.getHorseId())
                            + "\" hiện ở " + horse.getCurrentCountry() + " nhưng điểm đón ở " + request.getOriginCountry()
                            + ", không thể chọn vào đơn này.");
                }
                List<HorseDocument> horseDocuments = horseDocumentRepository.findByHorseID_Id(horseId);
                // Khách đã tải giấy tờ lên là đủ để đặt; Chuyên viên thẩm định hồ sơ ngựa sau, trong từng đơn
                for (DocumentRequirement requirement : requiredHorseDocuments) {
                    boolean submitted = horseDocuments.stream()
                            .anyMatch(document -> DocumentRequirementPolicy.matchesHorseDocument(
                                    requirement.getDocumentType(), document.getDocumentType()));
                    if (!submitted) {
                        throw new IllegalArgumentException("Ngựa \""
                                + (horse.getHorseName() != null ? horse.getHorseName() : hDto.getHorseId())
                                + "\" còn thiếu giấy tờ bắt buộc: "
                                + requirement.getDocumentName() + ".");
                    }
                }

                if ("IN_TRANSIT".equalsIgnoreCase(horse.getTransitStatus())) {
                    throw new IllegalStateException("Ngựa \"" + (horse.getHorseName() != null ? horse.getHorseName() : hDto.getHorseId())
                            + "\" (" + hDto.getHorseId() + ") đang trong hành trình vận chuyển (IN_TRANSIT), không thể chọn vào đơn mới.");
                }

                if (horseToActiveBookingMap.containsKey(horseId)) {
                    String activeBookingInfo = horseToActiveBookingMap.get(horseId);
                    throw new IllegalStateException("Ngựa \"" + (horse.getHorseName() != null ? horse.getHorseName() : hDto.getHorseId())
                            + "\" (" + hDto.getHorseId() + ") đã được đặt trong đơn hàng " + activeBookingInfo + " chưa hoàn tất, không thể chọn vào đơn mới.");
                }
            }
        }

        // 7. Tạo đơn hàng Booking mới
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
                .consignorIdCountry(request.getConsignor() != null ? trimToNull(request.getConsignor().getIdCountry()) : null)
                .consignorIdType(request.getConsignor() != null ? trimToNull(request.getConsignor().getIdType()) : null)
                .consignorAddress(request.getConsignor() != null ? trimToNull(request.getConsignor().getAddress()) : null)
                .consigneeIdNumber(request.getConsignee() != null ? trimToNull(request.getConsignee().getIdNumber()) : null)
                .consigneeIdCountry(request.getConsignee() != null ? trimToNull(request.getConsignee().getIdCountry()) : null)
                .consigneeIdType(request.getConsignee() != null ? trimToNull(request.getConsignee().getIdType()) : null)
                .consigneeAddress(request.getConsignee() != null ? trimToNull(request.getConsignee().getAddress()) : null)
                .pickupAddress(request.getOriginName() != null ? request.getOriginName().trim() : "")
                .dropoffAddress(request.getDestName() != null ? request.getDestName().trim() : "")
                .totalHorses(request.getHorses() != null ? request.getHorses().size() : 1)
                .detailsJson(detailsJson)
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        // 8. Ghi Audit Log vào SYSTEM_LOG
        try {
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
        } catch (Exception ex) {
            log.warn("Failed to write system log for CREATE_BOOKING: {}", ex.getMessage());
        }

        log.info("Successfully created booking ID={} for user={}", savedBooking.getId(), customerUsername);
        return mapToResponse(savedBooking);
    }

    @Override
    public List<BookingResponse> getCustomerBookings(String customerUsername) {
        User user = userRepository.findByUsernameOrEmail(customerUsername, customerUsername)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng: " + customerUsername));

        return bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(user.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public PagedResponse<BookingResponse> getCustomerBookingsPaged(String customerUsername, int page, int size) {
        User user = userRepository.findByUsernameOrEmail(customerUsername, customerUsername)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng: " + customerUsername));

        int safePage = Math.max(0, page);
        int safeSize = size > 0 ? Math.min(size, 100) : 20;
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<Booking> pageResult = bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(user.getId(), pageable);
        List<BookingResponse> content = pageResult.getContent().stream().map(this::mapToResponse).toList();

        return PagedResponse.<BookingResponse>builder()
                .content(content)
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .last(pageResult.isLast())
                .build();
    }

    @Override
    public BookingResponse getBookingById(Integer id, String customerUsername) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng có ID: " + id));

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

    // Xe và kíp xe chỉ hiện cho khách từ lúc có báo giá; đơn chưa duyệt, hết hạn, hủy hoặc bị từ chối thì không
    private static final Set<String> TEAM_HIDDEN_STATUSES = Set.of(
            "pending_intake", "under_review", "resubmission_requested", "pending_commercial",
            "quote_expired", "cancelled", "rejected");

    @Override
    @Transactional(readOnly = true)
    public List<CustomerTripTeamResponse> getCustomerTripTeam(Integer id, String customerUsername) {
        BookingResponse booking = getBookingById(id, customerUsername); // kiểm tra đơn thuộc khách đang đăng nhập
        if (booking.getStatus() == null || TEAM_HIDDEN_STATUSES.contains(booking.getStatus()) || booking.getTrips() == null) {
            return List.of();
        }
        Map<String, String> horseNames = new HashMap<>();
        if (booking.getHorses() != null) {
            booking.getHorses().forEach(h -> horseNames.put(h.getHorseId(), h.getName()));
        }
        List<CustomerTripTeamResponse> team = new ArrayList<>();
        for (VehicleTripDto trip : booking.getTrips()) {
            try {
                Vehicle vehicle = fleetService.findVehicleByIdOrCode(trip.getVehicleId());
                Staff driver = fleetService.findStaffByIdOrCode(trip.getDriverId(), "DRIVER");
                Staff escort = fleetService.findStaffByIdOrCode(trip.getEscortId(), "ESCORT");
                team.add(CustomerTripTeamResponse.builder()
                        .tripId(trip.getTripId())
                        .horseNames(trip.getHorseIds() == null ? List.of()
                                : trip.getHorseIds().stream().map(hid -> horseNames.getOrDefault(hid, hid)).toList())
                        .vehicle(CustomerTripTeamResponse.TeamVehicle.builder()
                                .plate(vehicle.getLicensePlate()).kind(vehicle.getVehicleName()).stalls(vehicle.getCapacity()).build())
                        .driver(teamMember(driver))
                        .escort(teamMember(escort))
                        .build());
            } catch (IllegalArgumentException ex) {
                // Chuyến chưa đủ xe hoặc kíp xe (đang chốt dở): bỏ qua như trước đây frontend vẫn làm
                log.debug("Bỏ qua chuyến {} của đơn {}: {}", trip.getTripId(), id, ex.getMessage());
            }
        }
        return team;
    }

    private CustomerTripTeamResponse.TeamMember teamMember(Staff staff) {
        return CustomerTripTeamResponse.TeamMember.builder()
                .name(staff.getUser() != null ? staff.getUser().getUsername() : "Nhân viên " + staff.getId())
                .phone(staff.getPhone())
                .build();
    }

    @Override
    @Transactional
    public BookingResponse resubmitBooking(String bookingIdentifier, String customerUsername) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));

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
        appendHistory(payload, customerUsername, "Bổ sung hồ sơ và gửi lại thẩm định");
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

    @Override
    public PagedResponse<BookingResponse> getBookingsPaged(String status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = size > 0 ? Math.min(size, 100) : 20;
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<Booking> pageResult;
        if (status == null || status.trim().isEmpty() || "ALL".equalsIgnoreCase(status)) {
            pageResult = bookingRepository.findAllByOrderByBookingDateDesc(pageable);
        } else {
            pageResult = bookingRepository.findByStatusOrderByBookingDateDesc(status, pageable);
        }

        List<BookingResponse> content = pageResult.getContent().stream().map(this::mapToResponse).toList();

        return PagedResponse.<BookingResponse>builder()
                .content(content)
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .last(pageResult.isLast())
                .build();
    }

    private static final Set<String> FIELD_STATUSES = Set.of(
            "waybill_issued", "clearance_in_progress", "clearance_done", "ready_for_pickup",
            "en_route_to_pickup", "in_transit", "incident_reported",
            "pending_emergency_approval", "emergency_plan_active",
            // Tới điểm giao và quá hạn bàn giao: kíp xe vẫn đang giữ ngựa nên vẫn phải thấy chuyến
            "arrived", "handover_timeout", "returning_to_hq",
            "delivered_pending_settlement"
    );

    // Đã giao xong và sang quyết toán: kíp xe vẫn thấy chuyến cho tới khi bấm "Đã về trụ sở"
    private static final Set<String> AFTER_DELIVERY_STATUSES = Set.of(
            "expenses_submitted", "settlement_issued", "payment_overdue", "completed"
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
        Set<String> statuses = new HashSet<>(FIELD_STATUSES);
        statuses.addAll(AFTER_DELIVERY_STATUSES);
        return bookingRepository.findByStatusInOrderByBookingDateDesc(statuses).stream()
                .filter(b -> {
                    boolean afterDelivery = AFTER_DELIVERY_STATUSES.contains(b.getStatus());
                    if (!afterDelivery && assignedBookingIds.contains(b.getId())) {
                        return true;
                    }
                    if (b.getDetailsJson() == null) return false;
                    try {
                        BookingDetailsPayload payload = objectMapper.readValue(b.getDetailsJson(), BookingDetailsPayload.class);
                        if (payload.getTrips() == null) return false;
                        return payload.getTrips().stream().anyMatch(t -> {
                            String assigned = isEscort ? t.getEscortId() : t.getDriverId();
                            if (assigned == null || assigned.isBlank()) return false;
                            if (afterDelivery && (t.getHomeReturn() == null || t.getHomeReturn().get("backAt") != null)) return false;
                            return matchesStaffIdentifier(assigned, myCode, myId, username, myName, role);
                        });
                    } catch (Exception e) {
                        return false;
                    }
                })
                .map(this::mapToResponse)
                .toList();
    }

    private boolean matchesStaffIdentifier(String assigned, String staffCode, String staffId, String username, String name, String role) {
        String normalized = canonicalStaffIdentifier(assigned, role);
        return normalized.equals(canonicalStaffIdentifier(staffCode, role))
                || normalized.equals(canonicalStaffIdentifier(staffId, role))
                || normalized.equals(canonicalStaffIdentifier(username, role))
                || (!name.isBlank() && normalized.equals(canonicalStaffIdentifier(name, role)));
    }

    private String canonicalStaffIdentifier(String value, String role) {
        if (value == null) return "";
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if ("DRIVER".equalsIgnoreCase(role) && normalized.matches("^TX-\\d+$")) {
            return "DR-" + normalized.substring(3);
        }
        if ("ESCORT".equalsIgnoreCase(role) && normalized.matches("^(NV|HT)-\\d+$")) {
            return "ES-" + normalized.substring(normalized.indexOf('-') + 1);
        }
        return normalized;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getAssignedBookings(String username) {
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng: " + username));
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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));

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
                "Tiếp nhận đơn");

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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));

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
                "Từ chối đơn");

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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng ID: " + bookingIdentifier));

        if (request.getTrips() == null || request.getTrips().isEmpty()) {
            throw new IllegalArgumentException("Phương án điều phối phải có ít nhất 1 chuyến xe!");
        }
        if (request.getRoute() != null
                && !(request.getRoute() instanceof Map<?, ?> route && route.get("legs") instanceof List<?>)) {
            throw new IllegalArgumentException("Lộ trình phải có danh sách chặng!");
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
        BookingDetailsPayload payload = BookingDetailsJson.read(objectMapper, booking);

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
            appendHistory(payload, "Hệ thống", "Hồ sơ ngựa và phương án xe đã duyệt xong");
        } else {
            booking.setStatus("under_review");
            appendHistory(payload, "Hệ thống", "Xác nhận xe và lộ trình di chuyển");
        }

        if (request.getRouteId() != null) {
            Route corridor = routeRepository.findById(request.getRouteId()).orElse(null);
            booking.setRouteID(corridor);
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
        } catch (Exception ex) {
            // Nhật ký lỗi không được làm hỏng thao tác chính, nhưng phải để lại dấu vết
            log.warn("Không ghi được nhật ký hệ thống COORDINATOR_CONFIRM_PLAN: {}", ex.getMessage());
        }

        log.info("Coordinator successfully confirmed fleet plan for booking ID={}", updatedBooking.getId());
        return mapToResponse(updatedBooking);
    }

    @Override
    @Transactional
    public BookingResponse assignCrew(String bookingIdentifier, AssignCrewRequest request) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));

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
                "Chọn tài xế và hộ tống");
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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));

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

        // Tổng, cọc, số dư do backend tính từ các dòng và khoản điều chỉnh; bỏ qua các con số tổng client gửi
        BigDecimal depositRate = new BigDecimal(systemConfigService.getConfig("DEPOSIT_RATE").getConfigValue());
        int quoteValidHours = Integer.parseInt(systemConfigService.getConfig("QUOTE_VALID_HOURS").getConfigValue());
        QuoteTotals totals = QuoteTotals.of(request.getLines(), request.getAdjustments(), depositRate);
        BigDecimal total = totals.total();
        BigDecimal deposit = totals.deposit();
        BigDecimal balance = totals.balance();

        Instant now = Instant.now();
        Instant expiresAt = now.plus(quoteValidHours, ChronoUnit.HOURS);

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

        Quotation quotation = Quotation.builder()
                .booking(booking)
                .subtotal(totals.subtotal())
                .adjustmentAmount(totals.adjustments())
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
        quoteMap.put("subtotal", totals.subtotal());
        quoteMap.put("total", total);
        quoteMap.put("deposit", deposit);
        quoteMap.put("balance", balance);
        quoteMap.put("sentAt", now.toEpochMilli());
        quoteMap.put("expiresAt", expiresAt.toEpochMilli());
        quoteMap.put("sentBy", request.getBy() != null ? request.getBy() : "Quản lý");
        payload.setQuote(quoteMap);

        appendHistory(payload, request.getBy() != null ? request.getBy() : "Quản lý",
                "Duyệt và phát hành báo giá");
        savePayload(booking, payload);

        booking.setStatus("awaiting_payment");
        Booking updated = bookingRepository.save(booking);

        String bookingCode = "BK-" + updated.getId();
        notificationRepository.save(Notification.builder()
                .recipientRole("customer")
                .recipientUsername(updated.getCustomerID().getUser().getUsername())
                .title("Báo giá đơn hàng " + bookingCode + " đã sẵn sàng")
                .message("Đơn hàng " + bookingCode + " đã có báo giá. Vui lòng xem chi tiết và đặt cọc trong thời hạn hiệu lực.")
                .link("/orders/" + bookingCode)
                .bookingId(bookingCode)
                .isRead(false)
                .createdAt(now)
                .build());

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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));

        if (!"pending_commercial".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Chỉ trả lại được đơn đang chờ duyệt báo giá!");
        }

        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Cần ghi lý do trả lại đơn!");
        }

        BookingDetailsPayload payload = extractPayload(booking);
        String toTarget = request.getTo() != null ? request.getTo().trim().toLowerCase() : "both";
        boolean toSpecialist = "specialist".equals(toTarget) || "both".equals(toTarget);
        boolean toCoordinator = "coordinator".equals(toTarget) || "both".equals(toTarget);

        String label;
        if (toSpecialist && toCoordinator) {
            label = "cả Kiểm dịch viên (duyệt lại hồ sơ) và Điều phối viên (làm lại xe & lộ trình)";
            if (payload.getMedical() instanceof Map) {
                ((Map<String, Object>) payload.getMedical()).put("status", "pending");
            }
            bookingVehicleRepository.deleteByBookingID_Id(booking.getId());
            bookingAssignmentRepository.deleteByBookingID_Id(booking.getId());
            payload.setTrips(null);
            payload.setRoute(null);
            payload.setPlan(null);
            payload.setGate(null);
        } else if (toSpecialist) {
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
                "Trả lại thẩm định");
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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));

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
        if (medicalMap.get("resubmit") instanceof Map<?, ?> resubmitMap) {
            medicalMap.put("lastResubmit", resubmitMap);
        }
        medicalMap.remove("resubmit");
        medicalMap.put("resubmittedAt", System.currentTimeMillis());
        payload.setMedical(medicalMap);

        booking.setStatus("under_review");

        appendHistory(payload, customerUsername != null ? customerUsername : "Khách hàng",
                "Bổ sung hồ sơ");
        savePayload(booking, payload);

        Booking updated = bookingRepository.save(booking);
        logAction("RESUBMIT_DOCUMENTS", updated.getId(),
                "Khách hàng (" + customerUsername + ") gửi lại hồ sơ cho Chuyên viên kiểm dịch duyệt lại");
        log.info("Booking ID={} resubmitted documents by {}", updated.getId(), customerUsername);
        return mapToResponse(updated);
    }

    // Áp hiệu ứng sau khi đã nhận đủ tiền cọc (luồng VNPAY): chốt báo giá, cấp Vận đơn
    @Override
    @Transactional
    public Booking confirmDepositPaid(Payment payment, String actor) {
        Booking booking = payment.getBookingID();
        Instant paidAt = payment.getPaymentDate() != null ? payment.getPaymentDate() : Instant.now();

        quotationRepository.findTopByBookingIdOrderBySentAtDesc(booking.getId()).ifPresent(quotation -> {
            quotation.setStatus("ACCEPTED");
            quotationRepository.save(quotation);
        });

        String waybillNo = "VD-2026-" + String.format("%04d", booking.getId());
        booking.setWaybillNo(waybillNo);
        booking.setStatus("waybill_issued");

        BookingDetailsPayload payload = extractPayload(booking);
        Map<String, Object> paymentMap = new HashMap<>();
        paymentMap.put("paidAt", paidAt.toEpochMilli());
        paymentMap.put("amount", payment.getAmount());
        paymentMap.put("reference", payment.getTransactionCode());
        paymentMap.put("method", payment.getPaymentMethod());
        payload.setPayment(paymentMap);

        // Kéo tự động các quy định pháp lý tương ứng từ bảng REGULATION theo cửa khẩu của đơn
        initClearanceRegulationsForBooking(booking, payload);

        appendHistory(payload, actor != null ? actor : "Khách hàng",
                "Đặt cọc");
        savePayload(booking, payload);
        Booking updated = bookingRepository.save(booking);

        logAction("PAY_DEPOSIT", updated.getId(),
                "Khách hàng thanh toán tiền cọc (" + payment.getAmount() + " VND) qua " + payment.getPaymentMethod()
                        + ", mã GD " + payment.getTransactionCode() + ", cấp Vận đơn " + waybillNo);
        log.info("Deposit confirmed for booking ID={}, waybill={}", updated.getId(), waybillNo);
        return updated;
    }

    private void initClearanceRegulationsForBooking(Booking booking, BookingDetailsPayload payload) {
        boolean isInternational = "international".equalsIgnoreCase(booking.getBookingType());
        String gateName = payload.getGate();

        // 1. Tìm bản ghi Location loại BorderGate nếu là tuyến quốc tế
        Location borderGate = null;
        if (isInternational && gateName != null && !gateName.trim().isEmpty()) {
            String cleanGate = gateName.trim().toLowerCase();
            borderGate = locationRepository.findAll().stream()
                    .filter(l -> "BorderGate".equalsIgnoreCase(l.getLocationType()))
                    .filter(l -> {
                        String name = l.getLocationName() != null ? l.getLocationName().toLowerCase() : "";
                        return cleanGate.contains(name) || name.contains(cleanGate)
                                || (cleanGate.contains("mộc bài") && name.contains("mộc bài"))
                                || (cleanGate.contains("lao bảo") && name.contains("lao bảo"))
                                || (cleanGate.contains("tịnh biên") && name.contains("tịnh biên"))
                                || (cleanGate.contains("cầu treo") && name.contains("cầu treo"))
                                || (cleanGate.contains("hà tiên") && name.contains("hà tiên"))
                                || (cleanGate.contains("tây trang") && name.contains("tây trang"));
                    })
                    .findFirst()
                    .orElse(null);
        }

        // 2. Kéo danh sách Regulation từ database
        List<Regulation> applicableRegs = new ArrayList<>();
        // a. Các quy định chung (nội địa & chuẩn bị): locationID is null
        applicableRegs.addAll(regulationRepository.findByLocationIDIsNullAndStatus("ACTIVE"));

        // b. Nếu là quốc tế, kéo các quy định gắn riêng với Cửa khẩu đó
        if (isInternational) {
            if (borderGate != null) {
                applicableRegs.addAll(regulationRepository.findByLocationID_IdAndStatus(borderGate.getId(), "ACTIVE"));
            } else {
                applicableRegs.addAll(regulationRepository.findByStatus("ACTIVE").stream()
                        .filter(r -> r.getLocationID() != null)
                        .toList());
            }
        }
        boolean hasRequirementSnapshot = payload.getDocumentRequirements() != null;
        List<DocumentRequirementSnapshot> requiredClearanceDocuments = hasRequirementSnapshot
                ? payload.getDocumentRequirements().stream()
                        .filter(requirement -> "CLEARANCE".equals(requirement.getDocumentGroup()))
                        .toList()
                : List.of();
        String configuredDestinationCountry = payload.getDestCountry() != null ? payload.getDestCountry()
                : borderGate != null ? borderGate.getDestinationCountry() : null;
        Set<String> requiredClearanceTypes = requiredClearanceDocuments.stream()
                .map(DocumentRequirementSnapshot::getDocumentType)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, DocumentRequirementSnapshot> requirementByType = requiredClearanceDocuments.stream()
                .collect(java.util.stream.Collectors.toMap(
                        DocumentRequirementSnapshot::getDocumentType, requirement -> requirement, (first, ignored) -> first));
        applicableRegs = applicableRegs.stream()
                .filter(regulation -> ClearanceDocumentPolicy.isApplicable(regulation, isInternational))
                .filter(regulation -> !hasRequirementSnapshot
                        || ClearanceDocumentPolicy.isForDestinationCountry(regulation, configuredDestinationCountry))
                .filter(regulation -> !hasRequirementSnapshot
                        || requiredClearanceTypes.contains(regulation.getDocumentType()))
                .toList();

        // 3. Khởi tạo clearance.items
        List<Map<String, Object>> items = new ArrayList<>();
        for (Regulation reg : applicableRegs) {
            Map<String, Object> item = new HashMap<>();
            String type = reg.getDocumentType() != null ? reg.getDocumentType() : ("doc_" + reg.getId());
            item.put("type", type);
            DocumentRequirementSnapshot requirement = requirementByType.get(reg.getDocumentType());
            item.put("label", requirement != null ? requirement.getDocumentName() : reg.getRegulationName());
            item.put("hint", requirement != null && requirement.getDescription() != null
                    ? requirement.getDescription() : reg.getDescription());
            item.put("note", "");
            item.put("status", "todo");
            item.put("photos", new ArrayList<String>());
            item.put("regulationId", reg.getId());
            if (reg.getLocationID() != null) {
                item.put("gateName", reg.getLocationID().getLocationName());
                item.put("gateLocationId", reg.getLocationID().getId());
            }
            items.add(item);
        }
        Set<String> configuredTypesPresent = items.stream()
                .map(item -> String.valueOf(item.get("type")))
                .collect(java.util.stream.Collectors.toSet());
        if (hasRequirementSnapshot) {
            for (DocumentRequirementSnapshot requirement : requiredClearanceDocuments) {
                if (configuredTypesPresent.add(requirement.getDocumentType())) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("type", requirement.getDocumentType());
                    item.put("label", requirement.getDocumentName());
                    item.put("hint", requirement.getDescription());
                    item.put("note", "");
                    item.put("status", "todo");
                    item.put("photos", new ArrayList<String>());
                    items.add(item);
                }
            }
        }

        Map<String, Object> clearanceMap = new HashMap<>();
        clearanceMap.put("items", items);
        clearanceMap.put("horsesCleared", new ArrayList<String>());
        if (borderGate != null) {
            clearanceMap.put("gateName", borderGate.getLocationName());
            clearanceMap.put("gateLocationId", borderGate.getId());
        } else if (gateName != null) {
            clearanceMap.put("gateName", gateName);
        }
        payload.setClearance(clearanceMap);
    }

    // Áp hiệu ứng sau khi đã nhận đủ 70% còn lại (luồng VNPAY): đủ điều kiện cho xe xuất phát
    @Override
    @Transactional
    public Booking confirmBalancePaid(Payment payment, String actor) {
        Booking booking = payment.getBookingID();
        Instant paidAt = payment.getPaymentDate() != null ? payment.getPaymentDate() : Instant.now();

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
        paymentMap.put("balanceAmount", payment.getAmount());
        paymentMap.put("balancePaidAt", paidAt.toEpochMilli());
        paymentMap.put("balanceTransaction", payment.getTransactionCode());
        paymentMap.put("balanceMethod", payment.getPaymentMethod());
        paymentMap.put("isFullyPaidFixedRate", true);
        payload.setPayment(paymentMap);

        appendHistory(payload, actor != null ? actor : "Khách hàng",
                "Thanh toán phần còn lại");
        savePayload(booking, payload);
        Booking updated = bookingRepository.save(booking);

        logAction("PAY_BALANCE", updated.getId(),
                "Khách hàng thanh toán 70% còn lại (" + payment.getAmount() + " VND) qua " + payment.getPaymentMethod()
                        + ", mã GD " + payment.getTransactionCode() + " cho đơn BK-" + updated.getId());
        log.info("Balance confirmed for booking ID={}", updated.getId());
        return updated;
    }

    @Override
    @Transactional
    public BookingResponse rejectQuote(String bookingIdentifier, String customerUsername,
            com.example.racehorse_transport.dto.booking.RejectQuoteRequest request) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));

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
                "Từ chối báo giá");
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
                    appendHistory(payload, "Hệ thống",
                            "Báo giá hết hạn");
                    savePayload(booking, payload);
                    bookingRepository.save(booking);

                    logAction("QUOTE_AUTO_EXPIRED_RELEASE_FLEET", booking.getId(),
                            "Báo giá đơn BK-" + booking.getId()
                                    + " đã hết hạn. Đã tự động giải phóng xe và kíp xe.");
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
                        appendHistory(payload, "Hệ thống", "Bảng quyết toán quá hạn");
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

    private void snapshotSelectedServices(List<BookingHorseDto> horses) {
        if (horses == null) {
            return;
        }

        Map<String, Integer> categoryIds = Map.of(
                "stall", 1,
                "feed", 2,
                "insurance", 4);

        for (BookingHorseDto horse : horses) {
            Map<String, String> requestedCodes = horse.getSelectedServiceCodes();
            Map<String, BookingServiceSelectionDto> legacySelections = horse.getSelectedServices();
            if (requestedCodes == null && legacySelections == null) {
                continue;
            }

            Map<String, BookingServiceSelectionDto> resolved = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> category : categoryIds.entrySet()) {
                String serviceCode = requestedCodes != null ? requestedCodes.get(category.getKey()) : null;
                BookingServiceSelectionDto legacySelection = legacySelections != null
                        ? legacySelections.get(category.getKey())
                        : null;
                if ((serviceCode == null || serviceCode.isBlank()) && legacySelection == null) {
                    throw new IllegalArgumentException("Mỗi ngựa phải chọn đủ khoang, thức ăn và bảo hiểm.");
                }

                com.example.racehorse_transport.entity.Service service = serviceCode != null && !serviceCode.isBlank()
                        ? serviceRepository.findByServiceCodeIgnoreCase(serviceCode)
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "Không tìm thấy dịch vụ mã: " + serviceCode))
                        : serviceRepository.findById(legacySelection.getId())
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "Không tìm thấy dịch vụ ID: " + legacySelection.getId()));
                if (!"ACTIVE".equalsIgnoreCase(service.getStatus())) {
                    throw new IllegalArgumentException(
                            "Dịch vụ \"" + service.getServiceName() + "\" vừa được tạm ngưng. Vui lòng chọn lại.");
                }
                if (service.getServiceCode() == null || service.getBillingUnit() == null) {
                    throw new IllegalStateException(
                            "Dịch vụ \"" + service.getServiceName() + "\" chưa được cấu hình mã và đơn vị tính.");
                }
                if (service.getCategoryID() == null
                        || !category.getValue().equals(service.getCategoryID().getId())) {
                    throw new IllegalArgumentException(
                            "Dịch vụ \"" + service.getServiceName() + "\" không thuộc đúng nhóm lựa chọn.");
                }

                resolved.put(category.getKey(), BookingServiceSelectionDto.builder()
                        .id(service.getId())
                        .serviceCode(service.getServiceCode())
                        .serviceName(service.getServiceName())
                        .categoryId(service.getCategoryID().getId())
                        .categoryName(service.getCategoryID().getCategoryName())
                        .price(service.getPrice())
                        .billingUnit(service.getBillingUnit())
                        .description(service.getDescription())
                        .build());
            }

            horse.setSelectedServices(resolved);
            horse.setSelectedServiceCodes(resolved.entrySet().stream().collect(
                    LinkedHashMap::new,
                    (codes, entry) -> codes.put(entry.getKey(), entry.getValue().getServiceCode()),
                    LinkedHashMap::putAll));
            BookingServiceSelectionDto insurance = resolved.get("insurance");
            boolean declinedInsurance = "INSURANCE_DECLINED".equals(insurance.getServiceCode())
                    || insurance.getId() == 10
                    || insurance.getServiceName().toLowerCase(Locale.ROOT).contains("từ chối");
            horse.setInsuranceOpted(!declinedInsurance);
        }
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
        BookingDetailsPayload payload = BookingDetailsJson.read(objectMapper, booking);
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

    private void validateJourneyStart(Booking booking, BookingDetailsPayload payload, VehicleTripDto trip) {
        if (trip.getDepartedAt() == null) {
            throw new IllegalStateException("Xe cần xuất phát đến điểm đón trước khi bắt đầu vận chuyển.");
        }
        Map<String, Object> run = trip.getRun();
        if (run != null && run.get("startedAt") != null) {
            throw new IllegalStateException("Hành trình của chuyến xe đã bắt đầu.");
        }
        List<Map<String, Object>> checkpoints = run == null ? List.of() : checkpointMaps(run.get("checkpoints"));
        Map<String, Object> pickup = checkpoints.stream()
                .filter(cp -> "pickup".equals(cp.get("type")) || "pickup".equals(cp.get("id")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Tài xế cần xác nhận đã đến điểm đón trước khi xuất phát."));
        if (pickup.get("arrivedAt") == null || isBlank(pickup.get("photo"))) {
            throw new IllegalStateException("Cần xác nhận điểm đón kèm ảnh trước khi xuất phát.");
        }

        Map<?, ?> payment = payload.getPayment() instanceof Map<?, ?> p ? p : Map.of();
        if (!Boolean.TRUE.equals(payment.get("balancePaid"))) {
            throw new IllegalStateException("Khách hàng cần thanh toán đủ 70% còn lại trước khi xe xuất phát.");
        }
        if (isBlank(pickup.get("handoverPhoto"))) {
            throw new IllegalStateException("Cần ảnh biên bản giao nhận đã ký trước khi xuất phát.");
        }

        List<String> originals = stringList(pickup.get("originals"));
        if (!originals.containsAll(List.of(
                "Hộ chiếu ngựa bản gốc (FEI / National Passport)",
                "Sổ tiêm phòng",
                "Phiếu xét nghiệm EIA/EVA, bản gốc kèm 02 bản sao công chứng"))) {
            throw new IllegalStateException("Cần xác nhận đã nhận đủ hồ sơ gốc của ngựa trước khi xuất phát.");
        }

        Set<String> tripHorseIds = new HashSet<>(trip.getHorseIds() != null ? trip.getHorseIds() : List.of());
        Set<String> scanned = new HashSet<>();
        for (String chip : stringList(pickup.get("chips"))) scanned.add(chip.toUpperCase());
        if (payload.getHorses() != null) {
            for (BookingHorseDto horse : payload.getHorses()) {
                if (horse.getHorseId() == null || !tripHorseIds.contains(horse.getHorseId())
                        || "UNFIT".equalsIgnoreCase(horse.getStatus())) continue;
                if (isBlank(horse.getMicrochip()) || !scanned.contains(horse.getMicrochip().toUpperCase())) {
                    throw new IllegalStateException("Cần quét microchip của tất cả ngựa đủ điều kiện trên chuyến trước khi xuất phát.");
                }
            }
        }
    }

    private List<Map<String, Object>> ensureRouteCheckpoints(Booking booking, BookingDetailsPayload payload, VehicleTripDto trip) {
        Map<String, Object> run = trip.getRun() != null ? trip.getRun() : new HashMap<>();
        List<Map<String, Object>> existing = checkpointMaps(run.get("checkpoints"));
        Map<String, Map<String, Object>> byId = new HashMap<>();
        for (Map<String, Object> cp : existing) {
            if (cp.get("id") != null) byId.put(String.valueOf(cp.get("id")), cp);
        }

        Object routeValue = payload.getRoute();
        Map<String, Object> route = objectMap(routeValue);
        Map<String, Object> pickup = byId.getOrDefault("pickup", new HashMap<>());
        pickup.put("id", "pickup");
        pickup.put("type", "pickup");
        pickup.putIfAbsent("label", "Nhận ngựa tại điểm đón");
        pickup.putIfAbsent("place", booking.getPickupAddress() != null ? booking.getPickupAddress() : "Điểm đón");
        if (run.get("startedAt") != null && pickup.get("arrivedAt") != null) {
            pickup.putIfAbsent("doneAt", run.get("startedAt"));
        }

        List<Map<String, Object>> ordered = new ArrayList<>();
        ordered.add(pickup);
        List<Map<String, Object>> middle = new ArrayList<>();
        List<Map<String, Object>> legs = route == null ? List.of() : mapList(route.get("legs"));
        List<Map<String, Object>> rests = route == null ? List.of() : mapList(route.get("rests"));
        for (int i = 0; i < rests.size(); i++) {
            Map<String, Object> rest = rests.get(i);
            String id = "rest-" + (i + 1);
            Map<String, Object> cp = byId.getOrDefault(id, new HashMap<>());
            cp.put("id", id);
            cp.put("type", "rest");
            cp.putIfAbsent("label", "Trạm nghỉ " + (i + 1));
            cp.putIfAbsent("place", String.valueOf(rest.getOrDefault("name", "Trạm nghỉ " + (i + 1))));
            long plannedAt = legs.isEmpty() ? System.currentTimeMillis()
                    : number(legs.get(Math.min(i, legs.size() - 1)).get("arriveAt"), System.currentTimeMillis());
            cp.putIfAbsent("plannedAt", plannedAt);
            middle.add(cp);
        }

        long borderEta = route == null ? 0L : number(route.get("borderEta"), 0L);
        if ("international".equalsIgnoreCase(booking.getBookingType()) && borderEta > 0) {
            String gate = payload.getGate() != null && !payload.getGate().isBlank() ? payload.getGate() : "Cửa khẩu";
            Map<String, Object> border = byId.getOrDefault("border", new HashMap<>());
            border.put("id", "border"); border.put("type", "border");
            border.putIfAbsent("label", "Tới cửa khẩu"); border.putIfAbsent("place", gate); border.putIfAbsent("plannedAt", borderEta);
            middle.add(border);
            Map<String, Object> customs = byId.getOrDefault("customs", new HashMap<>());
            customs.put("id", "customs"); customs.put("type", "customs");
            customs.putIfAbsent("label", "Hoàn tất thông quan"); customs.putIfAbsent("place", gate); customs.putIfAbsent("plannedAt", borderEta + 90 * 60_000L);
            middle.add(customs);
        }
        middle.sort((a, b) -> {
            int byTime = Long.compare(number(a.get("plannedAt"), 0L), number(b.get("plannedAt"), 0L));
            if (byTime != 0) return byTime;
            return "border".equals(a.get("type")) ? -1 : "border".equals(b.get("type")) ? 1 : 0;
        });
        ordered.addAll(middle);

        Map<String, Object> delivery = byId.getOrDefault("delivery", new HashMap<>());
        delivery.put("id", "delivery"); delivery.put("type", "delivery");
        delivery.putIfAbsent("label", "Bàn giao tại điểm giao");
        delivery.putIfAbsent("place", booking.getDropoffAddress() != null ? booking.getDropoffAddress() : "Điểm giao");
        long lastArrival = legs.isEmpty() ? System.currentTimeMillis() : number(legs.get(legs.size() - 1).get("arriveAt"), System.currentTimeMillis());
        long lastPlanned = middle.isEmpty() ? 0L : number(middle.get(middle.size() - 1).get("plannedAt"), 0L);
        delivery.putIfAbsent("plannedAt", Math.max(lastArrival, lastPlanned + (middle.isEmpty() ? 0L : 30 * 60_000L)));
        ordered.add(delivery);
        return ordered;
    }

    private List<Map<String, Object>> checkpointMaps(Object value) {
        return mapList(value);
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) return new ArrayList<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = objectMap(item);
            if (map != null) result.add(map);
        }
        return result;
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return null;
        Map<String, Object> result = new HashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(item -> item != null).map(String::valueOf).toList();
    }

    private boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private long number(Object value, long fallback) {
        if (value instanceof Number n) return n.longValue();
        try { return value == null ? fallback : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
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

    private static String normalizeCountryCode(String country) {
        String normalized = trimToNull(country);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CAMBODIA", "CAMPUCHIA" -> "KH";
            case "LAOS", "LAO", "LÀO" -> "LA";
            case "VIETNAM", "VIỆT NAM" -> "VN";
            default -> normalized;
        };
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
        } catch (Exception ex) {
            log.warn("Failed to write system log for action {}: {}", actionType, ex.getMessage());
        }
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
        if (payload != null && payload.getTrips() != null) {
            for (VehicleTripDto trip : payload.getTrips()) {
                Map<String, Object> run = trip.getRun();
                if (run != null && run.get("startedAt") != null) {
                    run.put("checkpoints", ensureRouteCheckpoints(b, payload, trip));
                    trip.setRun(run);
                }
            }
        }
        if (medical == null) {
            Map<String, Object> defMed = new HashMap<>();
            defMed.put("status", "pending");
            medical = defMed;
        }

        Map<?, ?> paymentMap = payload != null && payload.getPayment() instanceof Map<?, ?> m ? m : Map.of();
        boolean balancePaid = Boolean.TRUE.equals(paymentMap.get("balancePaid"));
        boolean canCancel = CustomerBookingRules.canCancel(b.getStatus(), balancePaid);
        Object clearance = payload != null
                ? ClearanceDocumentPolicy.forBooking(payload.getClearance(), "international".equalsIgnoreCase(b.getBookingType()))
                : null;
        int storedTripCount = payload != null && payload.getTrips() != null ? payload.getTrips().size() : 0;
        int linkedVehicleCount = storedTripCount == 0 && b.getId() != null
                ? Math.toIntExact(bookingVehicleRepository.countByBookingID_Id(b.getId()))
                : 0;

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
                .originCountry(payload != null ? payload.getOriginCountry() : null)
                .destName(b.getDropoffAddress())
                .destCountry(payload != null ? payload.getDestCountry() : null)
                .consignor(PartyContactDto.consignorOf(b))
                .consignee(PartyContactDto.consigneeOf(b))
                .totalHorses(b.getTotalHorses() != null ? b.getTotalHorses() : 1)
                .vehicleCount(Math.max(storedTripCount, linkedVehicleCount))
                .horses(horseDtos)
                .waybillNo(b.getWaybillNo())
                .trips(payload != null ? payload.getTrips() : null)
                .routeId(b.getRouteID() != null ? b.getRouteID().getId() : null)
                .route(payload != null ? payload.getRoute() : null)
                .gate(payload != null ? payload.getGate() : null)
                .plan(payload != null ? payload.getPlan() : null)
                .quote(payload != null ? payload.getQuote() : null)
                .payment(payload != null ? payload.getPayment() : null)
                .canCancel(canCancel)
                .canPayBalance(CustomerBookingRules.canPayBalance(b.getStatus(), balancePaid))
                .cancelPenalty(canCancel && paymentMap.get("amount") != null
                        ? new BigDecimal(paymentMap.get("amount").toString()) : null)
                .medical(medical)
                .clearance(clearance)
                .settlement(payload != null ? payload.getSettlement() : null)
                .rating(payload != null ? payload.getRating() : null)
                .intake(payload != null ? payload.getIntake() : null)
                .rejection(payload != null ? payload.getRejection() : null)
                .history(payload != null ? payload.getHistory() : null)
                .build();
    }

    @Override
    @Transactional
    public BookingResponse updateTripProgress(String bookingIdentifier, com.example.racehorse_transport.dto.field.TripUpdateRequest request,
            String username, String role) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));

        BookingDetailsPayload payload = extractPayload(booking);
        List<com.example.racehorse_transport.dto.coordinator.VehicleTripDto> trips = payload.getTrips();
        if (trips == null || trips.isEmpty()) {
            throw new IllegalStateException("Đơn hàng chưa có thông tin chuyến xe!");
        }

        String tripId = request.getTripId();
        com.example.racehorse_transport.dto.coordinator.VehicleTripDto targetTrip = trips.stream()
                .filter(t -> t.getTripId() != null && t.getTripId().equalsIgnoreCase(tripId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown trip: " + tripId));
        String action = request.getAction() != null ? request.getAction() : "";
        validateTripUpdateActor(booking, targetTrip, action, username, role);
        Map<String, Object> activeRun = targetTrip.getRun();
        if (activeRun != null && activeRun.get("startedAt") != null) {
            activeRun.put("checkpoints", ensureRouteCheckpoints(booking, payload, targetTrip));
            targetTrip.setRun(activeRun);
        }

        String actor = username;
        Map<String, Object> reqPayload = request.getPayload() != null ? request.getPayload() : new HashMap<>();
        long now = System.currentTimeMillis();

        switch (action.toUpperCase()) {
            case "ACKNOWLEDGE":
            case "ACK": {
                String who = String.valueOf(reqPayload.getOrDefault("who", "driver"));
                Map<String, Object> acks = targetTrip.getAcks() != null ? targetTrip.getAcks() : new HashMap<>();
                acks.put(who, now);
                targetTrip.setAcks(acks);
                appendHistory(payload, actor, ("driver".equalsIgnoreCase(who) ? "Tài xế" : "Hộ tống") + " nhận lệnh điều xe");

                // Nếu đơn đã sẵn sàng hoặc đang di chuyển thì chỉ ghi nhận ack
                String currentStatus = booking.getStatus() != null ? booking.getStatus().toLowerCase() : "";
                if ("ready_for_pickup".equals(currentStatus) || "en_route_to_pickup".equals(currentStatus) || "in_transit".equals(currentStatus)) {
                    break;
                }

                // Kiểm tra xem kíp xe (Driver và Escort) của các chuyến đã xác nhận đủ chưa
                boolean allTripsAcked = trips.stream().allMatch(t -> {
                    Map<String, Object> a = t.getAcks();
                    return a != null && a.get("driver") != null && a.get("escort") != null;
                });

                if (allTripsAcked) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> clearanceMap = payload.getClearance() instanceof Map<?, ?> existingClr
                            ? new HashMap<>((Map<String, Object>) existingClr)
                            : new HashMap<>();

                    clearanceMap.put("crewAckedAt", now);

                    if (clearanceMap.get("doneAt") != null) {
                        booking.setStatus("ready_for_pickup");
                        appendHistory(payload, "Hệ thống", "Sẵn sàng đón ngựa");
                        log.info("Booking ID={} advanced to ready_for_pickup because clearance was already done", booking.getId());
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
                appendHistory(payload, actor, "Lập bộ giấy tờ bàn giao cho tài xế");
                break;
            }
            case "DEPART_TO_PICKUP": {
                if (targetTrip.getDepartedAt() != null) throw new IllegalStateException("Xe của chuyến này đã xuất phát.");
                // Chỉ xuất phát khi giấy tờ đã xong (Sẵn sàng đón ngựa) hoặc một xe khác của đơn đã xuất phát
                boolean ready = "ready_for_pickup".equalsIgnoreCase(booking.getStatus());
                if (!ready && trips.stream().noneMatch(t -> t.getDepartedAt() != null)) {
                    throw new IllegalStateException("Đơn chưa sẵn sàng đón ngựa, chưa thể xuất phát.");
                }
                targetTrip.setDepartedAt(now);
                if (ready) booking.setStatus("en_route_to_pickup");
                appendHistory(payload, actor, "Xe xuất phát đến điểm đón");
                break;
            }
            case "ARRIVE_PICKUP": {
                if (targetTrip.getDepartedAt() == null) throw new IllegalStateException("The vehicle must depart for pickup before checking in.");
                if (targetTrip.getRun() != null && targetTrip.getRun().get("startedAt") != null) {
                    throw new IllegalStateException("The transport journey has already started.");
                }
                if (isBlank(reqPayload.get("photo"))) throw new IllegalArgumentException("A pickup photo is required.");
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) run.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                String pickupLoc = booking.getPickupAddress() != null && !booking.getPickupAddress().isBlank()
                        ? booking.getPickupAddress() : "Điểm đón";
                if (checkpoints.isEmpty()) {
                    Map<String, Object> cp = new HashMap<>();
                    cp.put("id", "pickup");
                    cp.put("type", "pickup");
                    cp.put("label", "Điểm đón");
                    cp.put("place", pickupLoc);
                    cp.put("arrivedAt", now);
                    cp.put("photo", reqPayload.get("photo"));
                    checkpoints.add(cp);
                } else {
                    Map<String, Object> first = checkpoints.get(0);
                    first.putIfAbsent("id", "pickup");
                    first.putIfAbsent("type", "pickup");
                    first.putIfAbsent("label", "Điểm đón");
                    first.putIfAbsent("place", pickupLoc);
                    first.put("arrivedAt", now);
                    first.put("photo", reqPayload.get("photo"));
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
                String pickupLoc = booking.getPickupAddress() != null && !booking.getPickupAddress().isBlank()
                        ? booking.getPickupAddress() : "Điểm đón";
                if (checkpoints.isEmpty()) {
                    Map<String, Object> cp = new HashMap<>();
                    cp.put("id", "pickup");
                    cp.put("type", "pickup");
                    cp.put("label", "Điểm đón");
                    cp.put("place", pickupLoc);
                    checkpoints.add(cp);
                } else {
                    Map<String, Object> first = checkpoints.get(0);
                    first.putIfAbsent("id", "pickup");
                    first.putIfAbsent("type", "pickup");
                    first.putIfAbsent("label", "Điểm đón");
                    first.putIfAbsent("place", pickupLoc);
                }
                Map<String, Object> pickup = checkpoints.get(0);
                @SuppressWarnings("unchecked")
                List<String> chips = (List<String>) pickup.computeIfAbsent("chips", k -> new ArrayList<>());
                if (!chips.contains(chip) && !chip.isEmpty()) {
                    chips.add(chip);
                }
                targetTrip.setRun(run);
                appendHistory(payload, actor, "Quét mã microchip");
                break;
            }
            case "EXCLUDE_UNFIT_HORSE": {
                String horseId = String.valueOf(reqPayload.getOrDefault("horseId", "")).trim();
                String reason = String.valueOf(reqPayload.getOrDefault("reason", "Ốm / Sốt không đủ điều kiện khởi hành")).trim();
                String symptoms = String.valueOf(reqPayload.getOrDefault("symptoms", "")).trim();
                String photo = String.valueOf(reqPayload.getOrDefault("photo", "")).trim();
                Double temp = reqPayload.get("temperature") != null ? Double.valueOf(reqPayload.get("temperature").toString()) : null;
                BigDecimal discountAmount = BigDecimal.ZERO;
                if (reqPayload.get("discountAmount") != null) {
                    try {
                        discountAmount = new BigDecimal(reqPayload.get("discountAmount").toString());
                    } catch (NumberFormatException ex) {
                        // Số tiền sai thì dừng, không âm thầm giảm trừ 0 đồng
                        throw new IllegalArgumentException("Số tiền giảm trừ không hợp lệ: " + reqPayload.get("discountAmount"));
                    }
                }

                // 1. Cập nhật trạng thái cá thể ngựa trong payload
                String horseName = horseId;
                if (payload.getHorses() != null) {
                    for (BookingHorseDto h : payload.getHorses()) {
                        if (horseId.equalsIgnoreCase(h.getHorseId())) {
                            horseName = h.getName() != null ? h.getName() : horseId;
                            h.setStatus("UNFIT");
                            Map<String, Object> unfitInfo = new HashMap<>();
                            unfitInfo.put("reason", reason);
                            unfitInfo.put("symptoms", symptoms);
                            unfitInfo.put("photo", photo);
                            if (temp != null) {
                                unfitInfo.put("temperature", temp);
                            }
                            unfitInfo.put("at", now);
                            unfitInfo.put("by", actor);
                            unfitInfo.put("discountAmount", discountAmount.doubleValue());
                            h.setUnfitInfo(unfitInfo);
                            break;
                        }
                    }
                }

                // 2. Loại bỏ cá thể ngựa khỏi trip.horseIds và pickup chips
                if (targetTrip.getHorseIds() != null) {
                    targetTrip.getHorseIds().removeIf(id -> id.equalsIgnoreCase(horseId));
                }
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) run.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                if (!checkpoints.isEmpty()) {
                    Map<String, Object> pickup = checkpoints.get(0);
                    if (payload.getHorses() != null) {
                        for (BookingHorseDto h : payload.getHorses()) {
                            if (horseId.equalsIgnoreCase(h.getHorseId()) && h.getMicrochip() != null) {
                                @SuppressWarnings("unchecked")
                                List<String> chips = (List<String>) pickup.get("chips");
                                if (chips != null) {
                                    chips.remove(h.getMicrochip().toUpperCase());
                                }
                            }
                        }
                    }
                }
                targetTrip.setRun(run);

                // 3. Giảm trừ chi phí cho khách hàng
                if (discountAmount.compareTo(BigDecimal.ZERO) > 0 && payload.getQuote() instanceof Map<?, ?> rawQuoteMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> quoteMap = new HashMap<>((Map<String, Object>) rawQuoteMap);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> adjustments = new ArrayList<>();
                    if (quoteMap.get("adjustments") instanceof List<?> rawList) {
                        for (Object o : rawList) {
                            if (o instanceof Map<?, ?> m) {
                                adjustments.add(new HashMap<>((Map<String, Object>) m));
                            }
                        }
                    }

                    Map<String, Object> adj = new HashMap<>();
                    adj.put("label", "Giảm trừ không tiếp nhận ngựa " + horseName + " (" + reason + ")");
                    adj.put("amount", discountAmount.negate().doubleValue());
                    adjustments.add(adj);
                    quoteMap.put("adjustments", adjustments);

                    BigDecimal currentTotal = quoteMap.get("total") != null ? new BigDecimal(quoteMap.get("total").toString()) : BigDecimal.ZERO;
                    BigDecimal currentBalance = quoteMap.get("balance") != null ? new BigDecimal(quoteMap.get("balance").toString()) : BigDecimal.ZERO;
                    BigDecimal newTotal = currentTotal.subtract(discountAmount).max(BigDecimal.ZERO);
                    BigDecimal newBalance = currentBalance.subtract(discountAmount).max(BigDecimal.ZERO);

                    quoteMap.put("total", newTotal.doubleValue());
                    quoteMap.put("balance", newBalance.doubleValue());
                    payload.setQuote(quoteMap);

                    booking.setTotalBookingPrice(newTotal);
                    booking.setTotalHorses(Math.max(1, (booking.getTotalHorses() != null ? booking.getTotalHorses() : 1) - 1));

                    final BigDecimal finalDiscount = discountAmount;
                    quotationRepository.findTopByBookingIdOrderBySentAtDesc(booking.getId()).ifPresent(q -> {
                        q.setTotalAmount(newTotal);
                        q.setBalanceAmount(newBalance);
                        BigDecimal existingAdj = q.getAdjustmentAmount() != null ? q.getAdjustmentAmount() : BigDecimal.ZERO;
                        q.setAdjustmentAmount(existingAdj.subtract(finalDiscount));
                        quotationRepository.save(q);
                    });
                }

                // 4. Ghi log cảnh báo khẩn cấp an sinh
                Log emergencyLog = new Log();
                emergencyLog.setId((int) (logRepository.count() + 1));
                emergencyLog.setBookingID(booking);
                emergencyLog.setLogType("EMERGENCY_HEALTH_ALERT");
                emergencyLog.setStatus("UNFIT_FOR_TRAVEL");
                emergencyLog.setNote("TỪ CHỐI TIẾP NHẬN NGỰA TẠI ĐIỂM ĐÓN [" + horseName + "]: " + reason
                        + (temp != null ? " | Thân nhiệt: " + temp + "°C" : "")
                        + (!symptoms.isBlank() ? " | Triệu chứng: " + symptoms : "")
                        + " | Giảm trừ chi phí khách: " + discountAmount + " VND");
                emergencyLog.setCreatedAt(Instant.now());
                logRepository.save(emergencyLog);

                // 5. Ghi lịch sử đơn hàng
                appendHistory(payload, actor, "Từ chối tiếp nhận ngựa");
                break;
            }
            case "COLLECT_ORIGINALS": {
                Object items = reqPayload.get("items");
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) run.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                String pickupLoc = booking.getPickupAddress() != null && !booking.getPickupAddress().isBlank()
                        ? booking.getPickupAddress() : "Điểm đón";
                if (checkpoints.isEmpty()) {
                    Map<String, Object> cp = new HashMap<>();
                    cp.put("id", "pickup");
                    cp.put("type", "pickup");
                    cp.put("label", "Điểm đón");
                    cp.put("place", pickupLoc);
                    checkpoints.add(cp);
                } else {
                    Map<String, Object> first = checkpoints.get(0);
                    first.putIfAbsent("id", "pickup");
                    first.putIfAbsent("type", "pickup");
                    first.putIfAbsent("label", "Điểm đón");
                    first.putIfAbsent("place", pickupLoc);
                }
                checkpoints.get(0).put("originals", items);
                targetTrip.setRun(run);
                appendHistory(payload, actor, "Tài xế đã thu đủ hồ sơ gốc");
                break;
            }
            case "UPLOAD_HANDOVER": {
                String photo = String.valueOf(reqPayload.getOrDefault("photo", ""));
                String checkpointId = String.valueOf(reqPayload.getOrDefault("checkpointId", "pickup"));
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) run.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                Map<String, Object> handoverCheckpoint = checkpoints.stream()
                        .filter(cp -> checkpointId.equals(String.valueOf(cp.get("id"))))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown handover checkpoint: " + checkpointId));
                if (!"pickup".equals(handoverCheckpoint.get("type")) && !"delivery".equals(handoverCheckpoint.get("type"))) {
                    throw new IllegalStateException("Handover evidence is only accepted at pickup or delivery.");
                }
                if (isBlank(photo)) throw new IllegalArgumentException("A signed handover photo is required.");
                handoverCheckpoint.put("handoverPhoto", photo);
                targetTrip.setRun(run);
                appendHistory(payload, actor, "Tải ảnh biên bản giao nhận có chữ ký");
                break;
            }
            case "START_JOURNEY": {
                validateJourneyStart(booking, payload, targetTrip);
                booking.setStatus("in_transit");
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                List<Map<String, Object>> checkpoints = ensureRouteCheckpoints(booking, payload, targetTrip);
                if (checkpoints.isEmpty() || !"pickup".equals(String.valueOf(checkpoints.get(0).get("type")))) {
                    throw new IllegalStateException("Trip is missing its pickup checkpoint.");
                }
                checkpoints.get(0).put("doneAt", now);
                run.put("startedAt", now);
                run.put("checkpoints", checkpoints);
                targetTrip.setRun(run);
                appendHistory(payload, actor, "Bắt đầu vận chuyển");

                // Cập nhật trạng thái ngựa sang IN_TRANSIT
                if (payload.getHorses() != null) {
                    for (BookingHorseDto hDto : payload.getHorses()) {
                        if (hDto.getHorseId() != null && !hDto.getHorseId().isBlank()) {
                            try {
                                String numId = hDto.getHorseId().replaceAll("\\D", "");
                                if (!numId.isEmpty()) {
                                    Integer hId = Integer.parseInt(numId);
                                    horseRepository.findById(hId).ifPresent(h -> {
                                        h.setTransitStatus("IN_TRANSIT");
                                        horseRepository.save(h);
                                    });
                                }
                            } catch (Exception ex) {
                                log.warn("Không thể cập nhật transitStatus cho ngựa {}: {}", hDto.getHorseId(), ex.getMessage());
                            }
                        }
                    }
                }
                break;
            }
            case "ARRIVE_CHECKPOINT": {
                String cpId = String.valueOf(reqPayload.getOrDefault("checkpointId", ""));
                String photo = String.valueOf(reqPayload.getOrDefault("photo", ""));
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) run.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                if (run.get("startedAt") == null) throw new IllegalStateException("Journey has not started.");
                if (photo.isBlank()) throw new IllegalArgumentException("A checkpoint photo is required.");
                Map<String, Object> arrivedCheckpoint = null;
                for (Map<String, Object> cp : checkpoints) {
                    if (cpId.equals(cp.get("id"))) {
                        arrivedCheckpoint = cp;
                        break;
                    }
                }
                if (arrivedCheckpoint == null) throw new IllegalArgumentException("Unknown checkpoint: " + cpId);
                if ("pickup".equals(arrivedCheckpoint.get("type")) || "customs".equals(arrivedCheckpoint.get("type"))) {
                    throw new IllegalStateException("This checkpoint must be completed through its dedicated workflow.");
                }
                Map<String, Object> expectedCheckpoint = checkpoints.stream()
                        .filter(cp -> cp.get("doneAt") == null)
                        .findFirst()
                        .orElse(null);
                if (expectedCheckpoint == null || !cpId.equals(String.valueOf(expectedCheckpoint.get("id")))) {
                    throw new IllegalStateException("Check in at the next unfinished checkpoint in route order.");
                }
                arrivedCheckpoint.put("arrivedAt", now);
                arrivedCheckpoint.put("photo", photo);
                if ("border".equals(arrivedCheckpoint.get("type"))) arrivedCheckpoint.put("doneAt", now);
                targetTrip.setRun(run);
                appendHistory(payload, actor, "Xe tới trạm dừng");
                break;
            }
            case "SUBMIT_WELFARE": {
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> welfareList = (List<Map<String, Object>>) run.computeIfAbsent("welfare", k -> new ArrayList<>());
                String checkpointId = String.valueOf(reqPayload.getOrDefault("checkpointId", ""));
                if (run.get("startedAt") == null) throw new IllegalStateException("Journey has not started.");
                Map<String, Object> checkpoint = checkpointMaps(run.get("checkpoints")).stream()
                        .filter(cp -> checkpointId.equals(String.valueOf(cp.get("id"))))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown welfare checkpoint: " + checkpointId));
                if (!"rest".equals(checkpoint.get("type")) && !"delivery".equals(checkpoint.get("type"))) {
                    throw new IllegalStateException("Welfare logs are only accepted at rest stops and delivery.");
                }
                Map<String, Object> expectedWelfareCheckpoint = checkpointMaps(run.get("checkpoints")).stream()
                        .filter(cp -> cp.get("doneAt") == null)
                        .findFirst().orElse(null);
                if (expectedWelfareCheckpoint == null || !checkpointId.equals(String.valueOf(expectedWelfareCheckpoint.get("id")))) {
                    throw new IllegalStateException("Submit the welfare log at the current route checkpoint.");
                }
                if (checkpoint.get("arrivedAt") == null) throw new IllegalStateException("Check in at this checkpoint before submitting the welfare log.");
                if (isBlank(reqPayload.get("photo"))) throw new IllegalArgumentException("A welfare photo is required.");
                if (welfareList.stream().anyMatch(item -> checkpointId.equals(String.valueOf(item.get("checkpointId"))))) {
                    throw new IllegalStateException("A welfare log has already been submitted for this checkpoint.");
                }
                Map<String, Object> w = new HashMap<>(reqPayload);
                w.put("id", "welfare-" + now);
                w.put("checkpointId", checkpointId);
                w.put("at", now);
                w.put("by", actor);
                welfareList.add(w);
                targetTrip.setRun(run);
                appendHistory(payload, actor, "Hộ tống gửi nhật ký an sinh ngựa");
                break;
            }
            case "CONTINUE_JOURNEY": {
                Map<String, Object> journeyRun = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                if (journeyRun.get("startedAt") == null) throw new IllegalStateException("Journey has not started.");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> journeyCheckpoints = (List<Map<String, Object>>) journeyRun.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                Map<String, Object> customsStop = journeyCheckpoints.stream()
                        .filter(cp -> "customs".equals(cp.get("type")) && cp.get("doneAt") != null && cp.get("leftAt") == null)
                        .findFirst().orElse(null);
                if (customsStop != null) {
                    customsStop.put("leftAt", now);
                } else {
                    Map<String, Object> restStop = journeyCheckpoints.stream()
                            .filter(cp -> "rest".equals(cp.get("type")) && cp.get("arrivedAt") != null && cp.get("doneAt") == null)
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("No rest stop is waiting to continue."));
                    Map<String, Object> currentStop = journeyCheckpoints.stream()
                            .filter(cp -> cp.get("doneAt") == null)
                            .findFirst().orElse(null);
                    if (currentStop != restStop) throw new IllegalStateException("Continue from the current route checkpoint.");
                    String restId = String.valueOf(restStop.get("id"));
                    List<Map<String, Object>> welfare = mapList(journeyRun.get("welfare"));
                    if (welfare.stream().noneMatch(item -> restId.equals(String.valueOf(item.get("checkpointId"))))) {
                        throw new IllegalStateException("The escort must submit the horse welfare log before leaving the rest stop.");
                    }
                    restStop.put("doneAt", now);
                    restStop.put("leftAt", now);
                }
                targetTrip.setRun(journeyRun);
                appendHistory(payload, actor, "Xe tiếp tục hành trình");
                break;
            }
            case "CUSTOMS_CLEARED": {
                Map<String, Object> customsRun = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> customsCheckpoints = (List<Map<String, Object>>) customsRun.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                Map<String, Object> customs = customsCheckpoints.stream()
                        .filter(cp -> "customs".equals(cp.get("type")) && cp.get("doneAt") == null)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No customs checkpoint is waiting for clearance."));
                Map<String, Object> expectedCustoms = customsCheckpoints.stream()
                        .filter(cp -> cp.get("doneAt") == null)
                        .findFirst().orElse(null);
                if (expectedCustoms != customs) throw new IllegalStateException("Reach the border checkpoint before customs clearance.");
                Object stampPhotos = reqPayload.get("stampPhotos");
                if (!(stampPhotos instanceof List<?> photos) || photos.isEmpty()) {
                    throw new IllegalArgumentException("Stamped customs documents are required.");
                }
                customs.put("stampPhotos", stampPhotos);
                customs.putIfAbsent("arrivedAt", now);
                customs.put("doneAt", now);
                targetTrip.setRun(customsRun);
                appendHistory(payload, actor, "Hoàn tất thông quan");
                break;
            }
            case "RETURN_ORIGINALS": {
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                if (run.get("startedAt") == null) throw new IllegalStateException("Journey has not started.");
                String checkpointId = String.valueOf(reqPayload.getOrDefault("checkpointId", "delivery"));
                List<Map<String, Object>> checkpoints = checkpointMaps(run.get("checkpoints"));
                Map<String, Object> delivery = checkpoints.stream()
                        .filter(cp -> "delivery".equals(cp.get("type")) && checkpointId.equals(String.valueOf(cp.get("id"))))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Trip is missing its delivery checkpoint."));
                if (delivery.get("arrivedAt") == null) {
                    throw new IllegalStateException("Check in at the delivery point before confirming returned documents.");
                }
                if (delivery.get("doneAt") != null) {
                    throw new IllegalStateException("Delivery is already complete.");
                }
                boolean returned = Boolean.TRUE.equals(reqPayload.get("returnedOriginals"));
                delivery.put("returnedOriginals", returned);
                if (returned) {
                    delivery.put("returnedOriginalsAt", now);
                    delivery.put("returnedOriginalsBy", actor);
                } else {
                    delivery.remove("returnedOriginalsAt");
                    delivery.remove("returnedOriginalsBy");
                }
                run.put("checkpoints", checkpoints);
                targetTrip.setRun(run);
                appendHistory(payload, actor, returned
                        ? "Xác nhận trả chứng từ gốc"
                        : "Hủy xác nhận trả chứng từ gốc");
                break;
            }
            case "COMPLETE_DELIVERY": {
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                if (run.get("startedAt") == null) throw new IllegalStateException("Journey has not started.");
                Map<String, Object> delivery = checkpointMaps(run.get("checkpoints")).stream()
                        .filter(cp -> "delivery".equals(cp.get("type")))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Trip is missing its delivery checkpoint."));
                Map<String, Object> expectedDelivery = checkpointMaps(run.get("checkpoints")).stream()
                        .filter(cp -> cp.get("doneAt") == null)
                        .findFirst().orElse(null);
                if (expectedDelivery == null || !"delivery".equals(expectedDelivery.get("type"))) {
                    throw new IllegalStateException("Complete the earlier route checkpoints before delivery.");
                }
                if (delivery.get("arrivedAt") == null || isBlank(delivery.get("photo"))) {
                    throw new IllegalStateException("Check in at the delivery point with a photo before completing delivery.");
                }
                if (isBlank(delivery.get("handoverPhoto"))) {
                    throw new IllegalStateException("Upload the signed delivery handover record before completing delivery.");
                }
                if (!Boolean.TRUE.equals(delivery.get("returnedOriginals"))) {
                    throw new IllegalStateException("Confirm that the original documents were returned before completing delivery.");
                }
                List<Map<String, Object>> deliveryWelfare = mapList(run.get("welfare"));
                if (deliveryWelfare.stream().noneMatch(item -> "delivery".equals(String.valueOf(item.get("checkpointId"))))) {
                    throw new IllegalStateException("The escort must submit the final horse welfare log before delivery is complete.");
                }
                run.put("deliveredAt", now);
                Object deliveryCheckpoints = run.get("checkpoints");
                if (deliveryCheckpoints instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> cp && "delivery".equals(cp.get("type"))) {
                            @SuppressWarnings("unchecked") Map<Object, Object> mutable = (Map<Object, Object>) cp;
                            mutable.put("doneAt", now);
                        }
                    }
                }
                targetTrip.setRun(run);

                Map<String, Object> hr = new HashMap<>();
                hr.put("fromPlace", booking.getDropoffAddress() != null ? booking.getDropoffAddress() : "Điểm giao");
                hr.put("earliestAt", now + 1000);
                hr.put("eta", now + 7200000L);
                targetTrip.setHomeReturn(hr);

                booking.setStatus("delivered_pending_settlement");
                appendHistory(payload, actor, "Hoàn tất bàn giao ngựa");

                // Cập nhật trạng thái ngựa sang STABLE_IDLE khi giao xong
                if (payload.getHorses() != null) {
                    for (BookingHorseDto hDto : payload.getHorses()) {
                        if (hDto.getHorseId() != null && !hDto.getHorseId().isBlank()) {
                            try {
                                String numId = hDto.getHorseId().replaceAll("\\D", "");
                                if (!numId.isEmpty()) {
                                    Integer hId = Integer.parseInt(numId);
                                    horseRepository.findById(hId).ifPresent(h -> {
                                        h.setTransitStatus("STABLE_IDLE");
                                        if (booking.getDropoffAddress() != null && !booking.getDropoffAddress().isBlank()) {
                                            h.setCurrentLocationName(booking.getDropoffAddress());
                                        }
                                        horseRepository.save(h);
                                    });
                                }
                            } catch (Exception ex) {
                                log.warn("Không thể cập nhật transitStatus cho ngựa {}: {}", hDto.getHorseId(), ex.getMessage());
                            }
                        }
                    }
                }
                break;
            }
            case "CONFIRM_HOME": {
                Map<String, Object> hr = targetTrip.getHomeReturn();
                if (hr == null) throw new IllegalStateException("Chưa giao ngựa xong nên chưa thể xác nhận về trụ sở.");
                if (number(hr.get("earliestAt"), 0) > now) throw new IllegalStateException("Chưa tới thời điểm sớm nhất có thể về trụ sở.");
                if (isBlank(reqPayload.get("photo"))) throw new IllegalArgumentException("Cần ảnh chụp tại trụ sở.");
                hr.put("backAt", now);
                hr.put("photo", reqPayload.get("photo"));

                if (targetTrip.getVehicleId() != null) {
                    try {
                        Integer vId = Integer.parseInt(targetTrip.getVehicleId().replaceAll("[^0-9]", ""));
                        vehicleRepository.findById(vId).ifPresent(v -> {
                            v.setStatus("AVAILABLE");
                            vehicleRepository.save(v);
                        });
                    } catch (NumberFormatException ex) {
                        log.warn("Không nhả được xe {} khi về trụ sở: mã xe không hợp lệ", targetTrip.getVehicleId());
                    }
                }
                appendHistory(payload, actor, "Xe về tới trụ sở chính");
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

    private void validateTripUpdateActor(Booking targetBooking, VehicleTripDto targetTrip,
            String action, String username, String role) {
        if (!"DRIVER".equalsIgnoreCase(role) && !"ESCORT".equalsIgnoreCase(role)) return;

        Staff staff = staffRepository.findByUser_Username(username)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                        "Không tìm thấy hồ sơ nhân sự của tài khoản đăng nhập."));
        if (staff.getRole() == null || !staff.getRole().equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Vai trò của tài khoản không khớp với hồ sơ nhân sự.");
        }
        String staffCode = staff.getStaffCode() != null ? staff.getStaffCode() : "";
        String staffId = staff.getId() != null ? staff.getId().toString() : "";
        String staffName = staff.getUser() != null && staff.getUser().getUsername() != null
                ? staff.getUser().getUsername() : "";
        String assigned = "DRIVER".equalsIgnoreCase(role) ? targetTrip.getDriverId() : targetTrip.getEscortId();
        if (assigned == null || !matchesStaffIdentifier(assigned, staffCode, staffId, username, staffName, role)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không được phân công cho chuyến xe này.");
        }

        String normalizedAction = action.toUpperCase(java.util.Locale.ROOT);
        if (!"DEPART_TO_PICKUP".equals(normalizedAction) && !"ARRIVE_PICKUP".equals(normalizedAction)) return;
        if (!"DRIVER".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Chỉ tài xế được phép xuất phát hoặc xác nhận đến điểm đón.");
        }

        for (Booking booking : bookingRepository.findAll()) {
            BookingDetailsPayload payload = extractPayload(booking);
            if (payload.getTrips() == null) continue;
            String status = booking.getStatus() == null ? "" : booking.getStatus().toLowerCase(java.util.Locale.ROOT);
            for (VehicleTripDto trip : payload.getTrips()) {
                if (booking.getId().equals(targetBooking.getId())
                        && java.util.Objects.equals(trip.getTripId(), targetTrip.getTripId())) continue;
                if (!matchesStaffIdentifier(trip.getDriverId(), staffCode, staffId, username, staffName, role)) continue;
                boolean returning = trip.getHomeReturn() != null && trip.getHomeReturn().get("backAt") == null;
                boolean active = Set.of("en_route_to_pickup", "in_transit",
                        "incident_reported", "pending_emergency_approval", "emergency_plan_active",
                        "arrived", "handover_timeout", "returning_to_hq", "delivered_pending_settlement").contains(status);
                if (returning || active) {
                    throw new IllegalStateException("Bạn đang thực hiện chuyến BK-"
                            + String.format("%03d", booking.getId())
                            + " và chưa xác nhận về trụ sở, không thể thao tác chuyến khác.");
                }
            }
        }
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
            } catch (NumberFormatException ignored) {}
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
