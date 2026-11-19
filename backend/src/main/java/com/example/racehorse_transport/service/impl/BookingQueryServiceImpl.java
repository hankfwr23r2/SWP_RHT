package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.config.VnpayProperties;
import com.example.racehorse_transport.dto.booking.BookingDetailsPayloadDto;
import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.booking.CustomerTripTeamResponseDto;
import com.example.racehorse_transport.dto.booking.HorseInsuranceDto;
import com.example.racehorse_transport.dto.booking.PartyContactDto;
import com.example.racehorse_transport.dto.booking.PublicTrackingResponseDto;
import com.example.racehorse_transport.dto.common.PagedResponseDto;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingAssignmentRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingVehicleRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.example.racehorse_transport.security.PublicTrackingGuard;
import com.example.racehorse_transport.service.BookingQueryService;
import com.example.racehorse_transport.service.FleetService;
import com.example.racehorse_transport.util.BookingTripRules;
import com.example.racehorse_transport.util.BookingDetailsJson;
import com.example.racehorse_transport.service.BookingVisibilityService;
import com.example.racehorse_transport.util.ClearanceDocumentPolicy;
import com.example.racehorse_transport.util.CustomerBookingRules;
import com.example.racehorse_transport.util.DepositPaymentWindow;
import com.example.racehorse_transport.util.HorseInsurancePolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingQueryServiceImpl implements BookingQueryService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BookingAssignmentRepository bookingAssignmentRepository;
    private final FleetService fleetService;
    private final StaffRepository staffRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PublicTrackingGuard publicTrackingGuard;
    private final BookingVehicleRepository bookingVehicleRepository;
    private final QuotationRepository quotationRepository;
    private final PaymentRepository paymentRepository;
    private final VnpayProperties vnpayProperties;
    private final Clock clock;
    private final BookingVisibilityService bookingVisibility;

    // Xe và kíp xe chỉ hiện cho khách từ lúc có báo giá; đơn chưa duyệt, hết hạn, hủy hoặc bị từ chối thì không
    private static final Set<String> TEAM_HIDDEN_STATUSES = Set.of(
            "pending_intake", "under_review", "resubmission_requested", "pending_commercial",
            "quote_expired", "cancelled", "rejected");

    private static final Set<String> FIELD_STATUSES = Set.of(
            "waybill_issued", "clearance_in_progress", "clearance_done", "ready_for_pickup",
            "en_route_to_pickup", "pickup_failed", "returning_horses", "in_transit", "incident_reported",
            "pending_emergency_approval", "emergency_plan_active",
            // Tới điểm giao và quá hạn bàn giao: kíp xe vẫn đang giữ ngựa nên vẫn phải thấy chuyến
            "arrived", "handover_timeout", "returning_to_hq",
            "delivered_pending_settlement"
    );

    // Đã giao xong và sang quyết toán: kíp xe vẫn thấy chuyến cho tới khi bấm "Đã về trụ sở"
    private static final Set<String> AFTER_DELIVERY_STATUSES = Set.of(
            "expenses_submitted", "settlement_issued", "payment_overdue", "completed",
            // Hủy chuyến sau khi kíp đã trả ngựa: xe vẫn phải chạy về và xác nhận tại trụ sở
            "cancelled"
    );

    @Override
    public List<BookingResponseDto> getCustomerBookings(String customerUsername) {
        User user = userRepository.findByUsernameOrEmail(customerUsername, customerUsername)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng: " + customerUsername));

        return bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(user.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public PagedResponseDto<BookingResponseDto> getCustomerBookingsPaged(String customerUsername, int page, int size) {
        User user = userRepository.findByUsernameOrEmail(customerUsername, customerUsername)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng: " + customerUsername));

        int safePage = Math.max(0, page);
        int safeSize = size > 0 ? Math.min(size, 100) : 20;
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<Booking> pageResult = bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(user.getId(), pageable);
        List<BookingResponseDto> content = pageResult.getContent().stream().map(this::mapToResponse).toList();

        return PagedResponseDto.<BookingResponseDto>builder()
                .content(content)
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .last(pageResult.isLast())
                .build();
    }

    @Override
    public BookingResponseDto getBookingById(Integer id, String customerUsername) {
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

    @Override
    @Transactional(readOnly = true)
    public List<CustomerTripTeamResponseDto> getCustomerTripTeam(Integer id, String customerUsername) {
        BookingResponseDto booking = getBookingById(id, customerUsername); // kiểm tra đơn thuộc khách đang đăng nhập
        if (booking.getStatus() == null || TEAM_HIDDEN_STATUSES.contains(booking.getStatus()) || booking.getTrips() == null) {
            return List.of();
        }
        Map<String, String> horseNames = new HashMap<>();
        if (booking.getHorses() != null) {
            booking.getHorses().forEach(h -> horseNames.put(h.getHorseId(), h.getName()));
        }
        List<CustomerTripTeamResponseDto> team = new ArrayList<>();
        for (VehicleTripDto trip : booking.getTrips()) {
            try {
                Vehicle vehicle = fleetService.findVehicleByIdOrCode(trip.getVehicleId());
                Staff driver = fleetService.findStaffByIdOrCode(trip.getDriverId(), "DRIVER");
                Staff escort = fleetService.findStaffByIdOrCode(trip.getEscortId(), "ESCORT");
                team.add(CustomerTripTeamResponseDto.builder()
                        .tripId(trip.getTripId())
                        .horseNames(trip.getHorseIds() == null ? List.of()
                                : trip.getHorseIds().stream().map(hid -> horseNames.getOrDefault(hid, hid)).toList())
                        .vehicle(CustomerTripTeamResponseDto.TeamVehicle.builder()
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

    private CustomerTripTeamResponseDto.TeamMember teamMember(Staff staff) {
        return CustomerTripTeamResponseDto.TeamMember.builder()
                .name(staff.getUser() != null ? staff.getUser().getUsername() : "Nhân viên " + staff.getId())
                .phone(staff.getPhone())
                .build();
    }

    @Override
    public List<BookingResponseDto> getBookingsByStatus(String status) {
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
    public PagedResponseDto<BookingResponseDto> getBookingsPaged(String status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = size > 0 ? Math.min(size, 100) : 20;
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<Booking> pageResult;
        if (status == null || status.trim().isEmpty() || "ALL".equalsIgnoreCase(status)) {
            pageResult = bookingRepository.findAllByOrderByBookingDateDesc(pageable);
        } else {
            pageResult = bookingRepository.findByStatusOrderByBookingDateDesc(status, pageable);
        }

        List<BookingResponseDto> content = pageResult.getContent().stream().map(this::mapToResponse).toList();

        return PagedResponseDto.<BookingResponseDto>builder()
                .content(content)
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .last(pageResult.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponseDto> getMyFieldBookings(String username, String role) {
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
                        BookingDetailsPayloadDto payload = objectMapper.readValue(b.getDetailsJson(), BookingDetailsPayloadDto.class);
                        if (payload.getTrips() == null) return false;
                        return payload.getTrips().stream().anyMatch(t -> {
                            String assigned = isEscort ? t.getEscortId() : t.getDriverId();
                            if (assigned == null || assigned.isBlank()) return false;
                            if (afterDelivery && (t.getHomeReturn() == null || t.getHomeReturn().get("backAt") != null)) return false;
                            return BookingTripRules.matchesStaffIdentifier(assigned, myCode, myId, username, myName, role);
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
    public List<BookingResponseDto> getAssignedBookings(String username) {
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
    @Transactional(readOnly = true)
    public PublicTrackingResponseDto getPublicTracking(String code, String phoneLast4) {
        if (code == null || code.isBlank() || phoneLast4 == null || phoneLast4.isBlank()) {
            return null;
        }

        String trimmedCode = code.trim();
        String cleanPhoneLast4 = phoneLast4.replaceAll("\\D", "").trim();
        if (cleanPhoneLast4.length() < 4) {
            return null;
        }
        cleanPhoneLast4 = cleanPhoneLast4.substring(cleanPhoneLast4.length() - 4);

        // 1. Tìm booking theo mã đơn hoặc WaybillNo
        Optional<Booking> bookingOpt = bookingRepository.findByCode(trimmedCode.toUpperCase());
        if (bookingOpt.isEmpty()) {
            bookingOpt = bookingRepository.findByWaybillNoIgnoreCase(trimmedCode);
        }
        if (bookingOpt.isEmpty()) {
            return null;
        }

        Booking booking = bookingOpt.get();
        publicTrackingGuard.checkAllowed(booking.getId(), booking.getCode());

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
            publicTrackingGuard.recordFailure(booking.getId());
            return null;
        }
        publicTrackingGuard.recordSuccess(booking.getId());

        // 3. Map dữ liệu sang PublicTrackingResponseDto
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

        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);
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

        return PublicTrackingResponseDto.builder()
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
            case "waybill_issued", "clearance_in_progress", "clearance_done", "ready_for_pickup", "en_route_to_pickup", "pickup_failed", "returning_horses" -> 3;
            case "in_transit", "incident_reported", "pending_emergency_approval", "emergency_plan_active",
                 "delivered_pending_settlement", "expenses_submitted", "settlement_issued", "payment_overdue", "completed" -> 4;
            default -> 0;
        };
    }

    private String resolvePublicCustomerStatus(String status) {
        if (status == null) return "Đang xử lý";
        return switch (status.toLowerCase()) {
            case "pending_intake" -> "Đã gửi, chờ tiếp nhận";
            case "schedule_change_required" -> "Cần xác nhận lại lịch";
            case "under_review" -> "Đang thẩm định";
            case "pending_commercial" -> "Đang lập báo giá";
            case "awaiting_payment" -> "Chờ đặt cọc";
            case "quote_expired" -> "Báo giá hết hạn";
            case "waybill_issued" -> "Đã có vận đơn";
            case "clearance_in_progress" -> "Đang làm thủ tục giấy tờ";
            case "clearance_done" -> "Giấy tờ đã xong";
            case "ready_for_pickup" -> "Sẵn sàng đón ngựa";
            case "en_route_to_pickup" -> "Xe đang đến điểm đón";
            case "pickup_failed" -> "Không nhận được ngựa, chờ xử lý";
            case "returning_horses" -> "Đã hủy chuyến, chờ trả ngựa";
            case "in_transit" -> "Đang vận chuyển";
            case "incident_reported", "pending_emergency_approval" -> "Xe gặp sự cố, đang xử lý";
            case "emergency_plan_active" -> "Đang xử lý sự cố, ngựa được chăm sóc";
            case "delivered_pending_settlement" -> "Đã giao ngựa";
            case "expenses_submitted" -> "Đã giao ngựa";
            case "settlement_issued" -> "Đã giao ngựa";
            case "payment_overdue" -> "Đã giao ngựa";
            case "completed" -> "Đã hoàn tất";
            case "cancelled" -> "Đã hủy";
            case "rejected" -> "Đơn bị từ chối";
            default -> status;
        };
    }

    private String resolvePublicTone(String status) {
        if (status == null) return null;
        return switch (status.toLowerCase()) {
            case "schedule_change_required", "awaiting_payment", "settlement_issued", "emergency_plan_active" -> "warn";
            case "incident_reported", "pending_emergency_approval", "payment_overdue", "rejected" -> "bad";
            case "completed", "waybill_issued", "clearance_done", "delivered_pending_settlement" -> "done";
            default -> null;
        };
    }

    private Long depositConfirmationUntil(Booking booking) {
        if (!"awaiting_payment".equalsIgnoreCase(booking.getStatus())) return null;
        return quotationRepository.findTopByBookingIdOrderBySentAtDesc(booking.getId())
                .filter(q -> q.getExpiresAt() != null && "PENDING".equalsIgnoreCase(q.getStatus()))
                .filter(q -> clock.instant().isBefore(DepositPaymentWindow.confirmationUntil(q,
                        vnpayProperties.getDepositConfirmationGraceMinutes())))
                .filter(q -> DepositPaymentWindow.hasPendingDeposit(
                        paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(booking.getId()), q))
                .map(q -> DepositPaymentWindow.confirmationUntil(q,
                        vnpayProperties.getDepositConfirmationGraceMinutes()).toEpochMilli()).orElse(null);
    }

    @Override
    public BookingResponseDto mapToResponse(Booking b) {
        String formattedId = b.getCode();
        String customerUsername = "";
        String customerFullName = "";

        if (b.getCustomerID() != null) {
            customerFullName = b.getCustomerID().getFullName() != null ? b.getCustomerID().getFullName() : "";
            if (b.getCustomerID().getUser() != null) {
                customerUsername = b.getCustomerID().getUser().getUsername();
            }
        }

        BookingDetailsPayloadDto payload = null;
        List<BookingHorseDto> horseDtos = new ArrayList<>();
        if (b.getDetailsJson() != null && !b.getDetailsJson().trim().isEmpty()) {
            try {
                payload = BookingDetailsJson.read(objectMapper, b);
                if (payload.getHorses() != null) {
                    horseDtos = payload.getHorses();
                }
            } catch (Exception ex) {
                log.warn("Không thể đọc chi tiết đơn {}: {}", b.getId(), ex.getMessage());
            }
        }

        Object medical = payload != null ? payload.getMedical() : null;
        if (payload != null && payload.getTrips() != null) {
            for (VehicleTripDto trip : payload.getTrips()) {
                Map<String, Object> run = trip.getRun();
                if (run != null && run.get("startedAt") != null) {
                    run.put("checkpoints", BookingTripRules.ensureRouteCheckpoints(b, payload, trip));
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
        boolean horsesPickedUp = payload != null && "en_route_to_pickup".equals(b.getStatus()) && BookingTripRules.pickupComplete(payload);
        boolean canCancel = CustomerBookingRules.canCancel(b.getStatus(), balancePaid, horsesPickedUp);
        Object clearance = payload != null
                ? ClearanceDocumentPolicy.forBooking(payload.getClearance(), "international".equalsIgnoreCase(b.getBookingType()))
                : null;
        int storedTripCount = payload != null && payload.getTrips() != null ? payload.getTrips().size() : 0;
        int linkedVehicleCount = storedTripCount == 0 && b.getId() != null
                ? Math.toIntExact(bookingVehicleRepository.countByBookingID_Id(b.getId()))
                : 0;

        boolean customerView = bookingVisibility.isCustomerViewer();
        if (customerView && payload != null && payload.getQuote() == null) {
            for (BookingHorseDto horse : horseDtos) {
                var i = horse.getInsurance();
                if (i != null && "assistance".equals(i.getOption()))
                    horse.setInsurance(HorseInsuranceDto.builder().option("assistance").build());
            }
        }
        if (customerView && payload != null && payload.getQuote() instanceof Map<?, ?> quoteMap) {
            for (var horse : horseDtos) {
                var live = horse.getInsurance();
                if (live == null || !"assistance".equals(live.getOption())) continue;
                if (quoteMap.get("insuranceSnapshot") instanceof List<?> snapshots) {
                    for (Object row : snapshots) if (row instanceof Map<?, ?> snap && java.util.Objects.equals(horse.getHorseId(), snap.get("horseId")) && snap.get("insurance") != null) {
                        var quoted = objectMapper.convertValue(snap.get("insurance"), HorseInsuranceDto.class);
                        live.setProvider(quoted.getProvider()); live.setPremium(quoted.getPremium());
                        live.setDocuments(quoted.getDocuments()); live.setNote(quoted.getNote());
                        live.setQuoteValidUntil(quoted.getQuoteValidUntil()); live.setReviewed(quoted.getReviewed());
                    }
                }
                if (!Boolean.TRUE.equals(live.getArranged())) {
                    live.setPolicyNumber(null); live.setValidFrom(null); live.setValidUntil(null); live.setPolicyDocuments(null);
                }
            }
        }
        BookingResponseDto response = BookingResponseDto.builder()
                .id(formattedId)
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
                .trips(bookingVisibility.visibleTrips(payload))
                .routeId(b.getRouteID() != null ? b.getRouteID().getId() : null)
                .route(payload != null ? payload.getRoute() : null)
                .gate(payload != null ? payload.getGate() : null)
                .plan(payload != null ? payload.getPlan() : null)
                .photoEvidence(bookingVisibility.visibleEvidence(payload))
                .quote(payload != null ? payload.getQuote() : null)
                .payment(payload != null ? payload.getPayment() : null)
                .depositConfirmationUntil(depositConfirmationUntil(b))
                .canCancel(canCancel)
                .canPayBalance(CustomerBookingRules.canPayBalance(b.getStatus(), balancePaid) && payload != null && BookingTripRules.pickupComplete(payload))
                .cancelPenalty(canCancel && paymentMap.get("amount") != null
                        ? new BigDecimal(paymentMap.get("amount").toString()).subtract(
                            HorseInsurancePolicy.insuranceDeposit(payload != null ? payload.getQuote() : null, new BigDecimal(paymentMap.get("amount").toString()))) : null)
                .medical(medical)
                .clearance(clearance)
                .settlement(payload != null ? payload.getSettlement() : null)
                .rating(payload != null ? payload.getRating() : null)
                .intake(payload != null ? payload.getIntake() : null)
                .scheduleChange(payload != null ? payload.getScheduleChange() : null)
                .rejection(payload != null ? payload.getRejection() : null)
                .sendBack(payload != null ? payload.getSendBack() : null)
                .history(payload != null ? payload.getHistory() : null)
                .build();
        return customerView ? bookingVisibility.forCustomer(response) : response;
    }

    @Override
    public Integer resolveBookingId(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Thiếu mã đơn hàng.");
        return bookingRepository.findIdByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + code));
    }
}
