package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.util.TermsDocuments;
import com.example.racehorse_transport.repository.TermsAcceptanceRepository;
import com.example.racehorse_transport.entity.TermsAcceptance;
import com.example.racehorse_transport.config.VnpayProperties;
import com.example.racehorse_transport.dto.booking.BookingDetailsPayloadDto;
import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.booking.BookingServiceSelectionDto;
import com.example.racehorse_transport.dto.booking.CreateBookingRequestDto;
import com.example.racehorse_transport.dto.booking.CustomerTripTeamResponseDto;
import com.example.racehorse_transport.dto.booking.DocumentRequirementSnapshotDto;
import com.example.racehorse_transport.dto.booking.PublicTrackingResponseDto;
import com.example.racehorse_transport.dto.booking.RejectQuoteRequestDto;
import com.example.racehorse_transport.dto.common.PagedResponseDto;
import com.example.racehorse_transport.dto.coordinator.CoordinatorPlanRequestDto;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.dto.field.TripUpdateRequestDto;
import com.example.racehorse_transport.dto.manager.ActivateBookingRequestDto;
import com.example.racehorse_transport.dto.manager.AssignCrewRequestDto;
import com.example.racehorse_transport.dto.manager.CrewPickDto;
import com.example.racehorse_transport.dto.manager.QuoteLineDto;
import com.example.racehorse_transport.dto.manager.RejectBookingRequestDto;
import com.example.racehorse_transport.dto.manager.SendBackRequestDto;
import com.example.racehorse_transport.dto.manager.SendQuoteRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingAssignment;
import com.example.racehorse_transport.entity.BookingVehicle;
import com.example.racehorse_transport.entity.BookingVehicleId;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.DocumentRequirement;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.HorseDocument;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Notification;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingAssignmentRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingVehicleRepository;
import com.example.racehorse_transport.repository.DocumentRequirementRepository;
import com.example.racehorse_transport.repository.HorseDocumentRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.NotificationRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.repository.RouteRepository;
import com.example.racehorse_transport.repository.ServiceRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.example.racehorse_transport.service.BillMailService;
import static com.example.racehorse_transport.service.BillMailService.Payment.DEPOSIT;
import com.example.racehorse_transport.service.BookingQueryService;
import com.example.racehorse_transport.service.BookingService;
import com.example.racehorse_transport.service.CustomerProfileService;
import com.example.racehorse_transport.service.FleetService;
import com.example.racehorse_transport.service.OperationalLogService;
import com.example.racehorse_transport.service.StaffNotificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import com.example.racehorse_transport.service.TripProgressService;
import com.example.racehorse_transport.util.BookingCodes;
import com.example.racehorse_transport.util.BookingDetailsJson;
import com.example.racehorse_transport.util.BookingHistory;
import com.example.racehorse_transport.util.BookingSchedulePolicy;
import com.example.racehorse_transport.util.BookingTripRules;
import com.example.racehorse_transport.util.ClearanceDocumentPolicy;
import com.example.racehorse_transport.util.DepositPaymentWindow;
import com.example.racehorse_transport.util.DocumentRequirementPolicy;
import com.example.racehorse_transport.util.HorseInsurancePolicy;
import com.example.racehorse_transport.util.QuoteTotals;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final CustomerProfileService customerProfiles;
    private final HorseRepository horseRepository;
    private final OperationalLogService operationalLog;
    private final SystemConfigService systemConfigService;
    private final ServiceRepository serviceRepository;
    private final HorseDocumentRepository horseDocumentRepository;
    private final DocumentRequirementRepository documentRequirementRepository;
    private final StaffNotificationService staffNotifier;
    @Autowired
    private TermsAcceptanceRepository termsAcceptanceRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BookingVehicleRepository bookingVehicleRepository;
    private final BookingAssignmentRepository bookingAssignmentRepository;
    private final FleetService fleetService;
    private final RouteRepository routeRepository;
    private final Clock clock;
    private final QuotationRepository quotationRepository;
    private final NotificationRepository notificationRepository;
    private final PaymentRepository paymentRepository;
    private final VnpayProperties vnpayProperties;
    private final BillMailService billMailService;
    private final LocationRepository locationRepository;
    private final BookingQueryService bookingQueryService;
    private final TripProgressService tripProgressService;

    @Override
    @Transactional
    public BookingResponseDto createBooking(String customerUsername, CreateBookingRequestDto request) {
        // 1. Tìm thông tin User
        User user = userRepository.findByUsernameOrEmail(customerUsername, customerUsername)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng: " + customerUsername));

        // 2. Tìm hoặc tự động tạo Customer profile
        var consignor = request.getConsignor();
        Customer customer = customerProfiles.getOrCreate(user,
                consignor != null ? consignor.getName() : user.getUsername(),
                consignor != null ? consignor.getPhone() : "");

        List<Booking> customerBookings = bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(customer.getId());

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
        for (BookingHorseDto horse : request.getHorses()) {
            HorseInsurancePolicy.validateDeclaration(horse.getInsurance());
            var insurance = horse.getInsurance();
            insurance.setReviewed(false);
            insurance.setArranged(false);
            insurance.setPremium(null);
            insurance.setPolicyDocuments(null);
            insurance.setQuoteValidUntil(null);
            horse.setInsuranceOpted("assistance".equals(insurance.getOption()));
            if (!"personal".equals(insurance.getOption())) {
                insurance.setProvider(null); insurance.setPolicyNumber(null); insurance.setDocumentNames(null);
                insurance.setValidFrom(null); insurance.setValidUntil(null); insurance.setDocuments(null);
            }
        }
        snapshotSelectedServices(request.getHorses());
        boolean internationalBooking = "INTERNATIONAL".equalsIgnoreCase(request.getType());
        List<DocumentRequirement> bookingDocumentRequirements = documentRequirementRepository
                .findByStatusOrderByDocumentGroupAscDocumentNameAsc("ACTIVE")
                .stream()
                .filter(DocumentRequirement::isRequired)
                // Dòng theo cửa khẩu chỉ bổ sung chi tiết lúc làm thông quan, không đưa vào danh sách khách phải nộp
                .filter(requirement -> requirement.getLocation() == null)
                .filter(requirement -> DocumentRequirementPolicy.applies(
                        requirement, request.getType(), request.getDestCountry()))
                .filter(requirement -> internationalBooking
                        || !"CLEARANCE".equals(requirement.getDocumentGroup())
                        || !ClearanceDocumentPolicy.isInternationalOnlyType(requirement.getDocumentType()))
                .toList();

        // 5. Serialize cấu hình chi tiết ngựa và khởi tạo payload vào detailsJson
        String detailsJson = null;
        try {
            BookingDetailsPayloadDto initialPayload = new BookingDetailsPayloadDto();
            initialPayload.setOriginCountry(request.getOriginCountry());
            initialPayload.setDestCountry(request.getDestCountry());
            initialPayload.setHorses(request.getHorses() != null ? request.getHorses() : new ArrayList<>());
            initialPayload.setDocumentRequirements(bookingDocumentRequirements.stream()
                    .map(requirement -> DocumentRequirementSnapshotDto.builder()
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
                                BookingDetailsPayloadDto p = objectMapper.readValue(cb.getDetailsJson(), BookingDetailsPayloadDto.class);
                                if (p != null && p.getHorses() != null) {
                                    for (BookingHorseDto activeHorse : p.getHorses()) {
                                        if (activeHorse.getHorseId() != null) {
                                            String num = activeHorse.getHorseId().replaceAll("\\D", "");
                                            if (!num.isEmpty()) {
                                                Integer aId = Integer.valueOf(num);
                                                horseToActiveBookingMap.putIfAbsent(aId, "#" + cb.getCode() + " (" + cb.getStatus() + ")");
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

        boolean declinedInsurance = request.getHorses().stream()
                .anyMatch(horse -> "none".equals(horse.getInsurance().getOption()));
        if (declinedInsurance && !Boolean.TRUE.equals(request.getLimitedLiabilityAccepted())) {
            throw new IllegalArgumentException("Cần đồng ý Điều khoản Trách nhiệm Hạn chế cho các ngựa không mua bảo hiểm.");
        }

        // 7. Tạo đơn hàng Booking mới
        Booking booking = Booking.builder()
                .code(newUniqueCode())
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

        Instant acceptedAt = Instant.now();
        for (BookingHorseDto hDto : request.getHorses()) {
            if (!"none".equals(hDto.getInsurance().getOption())) continue;
            termsAcceptanceRepository.save(TermsAcceptance.builder()
                    .documentCode(TermsDocuments.LIMITED_LIABILITY)
                    .documentVersion(TermsDocuments.LIMITED_LIABILITY_VERSION)
                    .acceptedAt(acceptedAt)
                    .user(user)
                    .booking(savedBooking)
                    .horse(horseRepository.getReferenceById(Integer.valueOf(hDto.getHorseId().replaceAll("\\D", ""))))
                    .build());
        }

        // 8. Ghi nhật ký vận hành vào bảng log của booking
        try {
            operationalLog.record("CREATE_BOOKING", "BOOKING", savedBooking.getId(), "Tạo đơn vận chuyển " + savedBooking.getTotalHorses() + " con ngựa ("
                            + savedBooking.getPickupAddress() + " -> " + savedBooking.getDropoffAddress() + ")", user.getUsername());
        } catch (Exception ex) {
            log.warn("Failed to write operational log for CREATE_BOOKING: {}", ex.getMessage());
        }

        log.info("Successfully created booking ID={} for user={}", savedBooking.getId(), customerUsername);
        staffNotifier.toManagers(savedBooking, "Đơn mới chờ tiếp nhận · " + savedBooking.getCode(),
                "Khách vừa gửi đơn " + savedBooking.getTotalHorses() + " ngựa: " + savedBooking.getPickupAddress() + " → " + savedBooking.getDropoffAddress() + ".",
                "/manager/intake");
        return bookingQueryService.mapToResponse(savedBooking);
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
                if ("insurance".equals(category.getKey()) && horse.getInsurance() != null) continue;
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
            if (insurance == null) continue;
            boolean declinedInsurance = "INSURANCE_DECLINED".equals(insurance.getServiceCode())
                    || insurance.getId() == 10
                    || insurance.getServiceName().toLowerCase(Locale.ROOT).contains("từ chối");
            horse.setInsuranceOpted(!declinedInsurance);
        }
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

    // Mã đơn ngẫu nhiên chưa có trong DB. Trùng gần như không xảy ra (31^8 khả năng), vẫn kiểm tra
    // để không phải dựa vào lỗi UNIQUE lúc lưu đơn.
    private String newUniqueCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = BookingCodes.newCode();
            if (!bookingRepository.existsByCode(code)) return code;
        }
        throw new IllegalStateException("Không tạo được mã đơn hàng, vui lòng thử lại.");
    }

    @Override
    @Transactional
    public BookingResponseDto resubmitBooking(String bookingIdentifier, String customerUsername) {
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

        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);
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
        BookingHistory.appendWithDefaultActor(payload, customerUsername, "Bổ sung hồ sơ và gửi lại thẩm định");
        savePayload(booking, payload);

        return bookingQueryService.mapToResponse(bookingRepository.save(booking));
    }

    @Override
    @Transactional
    public BookingResponseDto activateBooking(String bookingIdentifier, ActivateBookingRequestDto request) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));

        if (!"pending_intake".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Đơn hàng không ở bước chờ tiếp nhận (pending_intake)!");
        }

        BookingSchedulePolicy.requireCurrentSchedule(booking, clock.instant());

        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);

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

        BookingHistory.appendWithDefaultActor(payload, request != null && request.getBy() != null ? request.getBy() : "Quản lý",
                "Tiếp nhận đơn");

        booking.setStatus("under_review");
        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);

        logAction("MANAGER_ACTIVATE_BOOKING", saved.getId(), "Quản lý tiếp nhận đơn " + saved.getCode() + " và kích hoạt thẩm định song song");
        String activatedCode = saved.getCode();
        staffNotifier.toAssignedSpecialist(saved, payload, "Đơn cần thẩm định · " + activatedCode,
                "Quản lý giao bạn thẩm định hồ sơ ngựa của đơn " + activatedCode + ".", "/specialist/verification/" + activatedCode);
        staffNotifier.toAssignedCoordinator(saved, payload, "Đơn cần chốt xe và lộ trình · " + activatedCode,
                "Quản lý giao bạn chọn xe và lập lộ trình cho đơn " + activatedCode + ".", "/coordinator/fleet-plan/" + activatedCode);
        log.info("Manager activated booking ID={} -> status=under_review", saved.getId());
        return bookingQueryService.mapToResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponseDto rejectBooking(String bookingIdentifier, RejectBookingRequestDto request) {
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

        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);
        String reason = (request != null && request.getReason() != null) ? request.getReason().trim() : "Từ chối đơn";

        Map<String, Object> rejectionMap = new HashMap<>();
        rejectionMap.put("at", System.currentTimeMillis());
        rejectionMap.put("by", request != null && request.getBy() != null ? request.getBy() : "Quản lý");
        rejectionMap.put("role", role);
        rejectionMap.put("reason", reason);

        if (payload.getHistory() == null) {
            payload.setHistory(new ArrayList<>());
        }
        BookingHistory.appendWithDefaultActor(payload, request != null && request.getBy() != null ? request.getBy() : "Quản lý",
                "Từ chối đơn");

        payload.setRejection(rejectionMap);
        booking.setStatus("rejected");
        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);

        logAction("REJECT_BOOKING", saved.getId(), "Từ chối đơn " + saved.getCode() + " bởi " + role + ": " + reason);
        log.info("Booking ID={} rejected by {}: reason={}", saved.getId(), role, reason);
        return bookingQueryService.mapToResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponseDto confirmCoordinatorPlan(String bookingIdentifier, CoordinatorPlanRequestDto request) {
        if (bookingIdentifier == null || bookingIdentifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã đơn hàng không được để trống!");
        }

        // 1. Phân giải Booking ID từ mã đơn
        Integer bookingId = parseBookingId(bookingIdentifier);

        // 2. Tìm đơn hàng
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng ID: " + bookingIdentifier));

        // Sửa được phương án tới khi Quản lý gửi báo giá; sau đó chốt lại sẽ kéo đơn ngược trạng thái
        if (!"under_review".equalsIgnoreCase(booking.getStatus()) && !"pending_commercial".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Chỉ chốt hoặc sửa phương án xe khi đơn đang thẩm định hoặc chờ duyệt báo giá!");
        }
        boolean wasWaitingForQuote = "pending_commercial".equalsIgnoreCase(booking.getStatus());
        if (request.getTrips() == null || request.getTrips().isEmpty()) {
            throw new IllegalArgumentException("Phương án điều phối phải có ít nhất 1 chuyến xe!");
        }
        BookingSchedulePolicy.requireCurrentSchedule(booking, clock.instant());
        if (request.getRoute() != null
                && !(request.getRoute() instanceof Map<?, ?> route && route.get("legs") instanceof List<?>)) {
            throw new IllegalArgumentException("Lộ trình phải có danh sách chặng!");
        }

        // 3. Kiểm tra tính khả dụng & tải trọng của từng xe
        Instant departDate = booking.getDepartureDate() != null ? booking.getDepartureDate() : Instant.now();
        List<BookingVehicle> newVehicles = new ArrayList<>();
        List<BookingHorseDto> bookedHorses = BookingDetailsJson.read(objectMapper, booking).getHorses();

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
            int stallsNeeded = stallsNeeded(bookedHorses, trip.getHorseIds());
            if (stallsNeeded > vehicle.getCapacity()) {
                throw new IllegalArgumentException(
                        "Xe " + vehicle.getLicensePlate() + " chỉ có " + vehicle.getCapacity()
                                + " ngăn, không thể chở " + trip.getHorseIds().size() + " con ngựa cần "
                                + stallsNeeded + " ngăn!");
            }

            // Kiểm tra lịch bận & khứ hồi về trụ sở VN
            fleetService.validateVehicleAvailability(vehicle.getId(), departDate, booking.getBookingType(),
                    booking.getId());

            // Chuẩn hóa tripId
            if (trip.getTripId() == null || trip.getTripId().trim().isEmpty()) {
                trip.setTripId("TRP-" + booking.getCode().substring(3) + "-" + (i + 1));
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
        BookingDetailsPayloadDto payload = BookingDetailsJson.read(objectMapper, booking);

        payload.setTrips(request.getTrips());
        payload.setRoute(request.getRoute());
        payload.setGate(request.getGate());

        Map<String, Object> planMap = new HashMap<>();
        planMap.put("at", System.currentTimeMillis());
        planMap.put("by", request.getBy() != null ? request.getBy() : "Điều phối viên");
        planMap.put("note", request.getNote() != null ? request.getNote() : "");
        payload.setPlan(planMap);
        if (payload.getSendBack() != null) payload.getSendBack().remove("coordinator"); // đã làm lại theo lý do Quản lý trả

        if (payload.getHistory() == null) {
            payload.setHistory(new ArrayList<>());
        }
        Map<String, Object> historyEntry = new HashMap<>();
        historyEntry.put("time", System.currentTimeMillis());
        historyEntry.put("actor", request.getBy() != null ? request.getBy() : "Điều phối viên");
        historyEntry.put("text", "Xác nhận " + request.getTrips().size() + " xe và lộ trình di chuyển");
        payload.getHistory().add(historyEntry);

        // 6. Kiểm tra điều kiện đồng bộ chuyển trạng thái (reviewDone gate)
        String medicalStatus = BookingTripRules.getMedicalStatus(payload);
        boolean medicalApproved = "approved".equalsIgnoreCase(medicalStatus);

        if (medicalApproved) {
            booking.setStatus("pending_commercial");
            BookingHistory.appendWithDefaultActor(payload, "Hệ thống", "Hồ sơ ngựa và phương án xe đã duyệt xong");
        } else {
            booking.setStatus("under_review");
            BookingHistory.appendWithDefaultActor(payload, "Hệ thống", "Xác nhận xe và lộ trình di chuyển");
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
        if ("pending_commercial".equals(updatedBooking.getStatus()) && !wasWaitingForQuote) {
            staffNotifier.toManagers(updatedBooking, "Đơn chờ duyệt báo giá · " + updatedBooking.getCode(),
                    "Chuyên viên đã duyệt hồ sơ và Điều phối viên đã chốt xe, lộ trình. Chọn kíp xe và gửi báo giá.", "/manager/approvals");
        }

        // 7. Ghi nhật ký hệ thống SYSTEM_LOG
        try {
            String logDesc = medicalApproved
                    ? "Điều phối viên (" + (request.getBy() != null ? request.getBy() : "coordinator") + ") chốt "
                            + request.getTrips().size() + " xe và lộ trình đơn " + updatedBooking.getCode()
                            + ", cả 2 bên đã đạt -> chuyển Quản lý duyệt báo giá."
                    : "Điều phối viên (" + (request.getBy() != null ? request.getBy() : "coordinator") + ") chốt "
                            + request.getTrips().size() + " xe và lộ trình đơn " + updatedBooking.getCode()
                            + ", đang chờ Chuyên viên kiểm dịch thẩm định hồ sơ ngựa.";
            operationalLog.record("COORDINATOR_CONFIRM_PLAN", "BOOKING", updatedBooking.getId(), logDesc, null);
        } catch (Exception ex) {
            // Nhật ký lỗi không được làm hỏng thao tác chính, nhưng phải để lại dấu vết
            log.warn("Không ghi được nhật ký hệ thống COORDINATOR_CONFIRM_PLAN: {}", ex.getMessage());
        }

        log.info("Coordinator successfully confirmed fleet plan for booking ID={}", updatedBooking.getId());
        return bookingQueryService.mapToResponse(updatedBooking);
    }

    @Override
    @Transactional
    public BookingResponseDto assignCrew(String bookingIdentifier, AssignCrewRequestDto request) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));
        BookingSchedulePolicy.requireCurrentSchedule(booking, clock.instant());

        if (!"pending_commercial".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException(
                    "Chỉ phân công kíp xe khi đơn đang ở bước duyệt báo giá (pending_commercial)!");
        }

        if (request.getPicks() == null || request.getPicks().isEmpty()) {
            throw new IllegalArgumentException("Danh sách phân công kíp xe không được để trống!");
        }

        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);
        String medicalStatus = BookingTripRules.getMedicalStatus(payload);
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
        BookingHistory.appendWithDefaultActor(payload, request.getBy() != null ? request.getBy() : "Quản lý",
                "Chọn tài xế và hộ tống");
        savePayload(booking, payload);

        Booking updated = bookingRepository.save(booking);

        logAction("ASSIGN_CREW", updated.getId(), "Quản lý (" + request.getBy() + ") phân công "
                + newAssignments.size() + " nhân sự kíp xe cho đơn " + updated.getCode());

        log.info("Successfully assigned crew for booking ID={}", updated.getId());
        return bookingQueryService.mapToResponse(updated);
    }

    @SuppressWarnings("unchecked")
    @Override
    @Transactional
    public BookingResponseDto sendBack(String bookingIdentifier, SendBackRequestDto request) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));

        if (!"pending_commercial".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Chỉ trả lại được đơn đang chờ duyệt báo giá!");
        }

        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);
        String toTarget = request.getTo() != null ? request.getTo().trim().toLowerCase() : "both";
        boolean toSpecialist = "specialist".equals(toTarget) || "both".equals(toTarget);
        boolean toCoordinator = "coordinator".equals(toTarget) || "both".equals(toTarget);

        // Mỗi bên nhận lý do riêng; "reason" chung chỉ dùng khi không ghi lý do riêng
        String specialistReason = firstText(request.getSpecialistReason(), request.getReason());
        String coordinatorReason = firstText(request.getCoordinatorReason(), request.getReason());
        if (toSpecialist && specialistReason == null) {
            throw new IllegalArgumentException("Cần ghi lý do trả lại cho Chuyên viên!");
        }
        if (toCoordinator && coordinatorReason == null) {
            throw new IllegalArgumentException("Cần ghi lý do trả lại cho Điều phối viên!");
        }
        Map<String, Object> sendBackMap = new HashMap<>();
        sendBackMap.put("at", clock.instant().toEpochMilli());
        sendBackMap.put("by", request.getBy() != null ? request.getBy() : "Quản lý");
        if (toSpecialist) sendBackMap.put("specialist", specialistReason);
        if (toCoordinator) sendBackMap.put("coordinator", coordinatorReason);
        payload.setSendBack(sendBackMap);

        String label;
        if (toSpecialist && toCoordinator) {
            label = "cả Chuyên viên (duyệt lại hồ sơ) và Điều phối viên (làm lại xe & lộ trình)";
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
            label = "Chuyên viên duyệt lại hồ sơ ngựa";
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
        BookingHistory.appendWithDefaultActor(payload, request.getBy() != null ? request.getBy() : "Quản lý",
                "Trả lại thẩm định");
        savePayload(booking, payload);

        Booking updated = bookingRepository.save(booking);
        String sentBackCode = updated.getCode();
        if (toSpecialist) staffNotifier.toAssignedSpecialist(updated, payload, "Quản lý trả lại đơn · " + sentBackCode,
                "Lý do: " + specialistReason, "/specialist/verification/" + sentBackCode);
        if (toCoordinator) staffNotifier.toAssignedCoordinator(updated, payload, "Quản lý trả lại đơn · " + sentBackCode,
                "Lý do: " + coordinatorReason, "/coordinator/fleet-plan/" + sentBackCode);
        logAction("SEND_BACK", updated.getId(),
                "Quản lý trả đơn " + updated.getCode() + " về " + toTarget
                        + (toSpecialist ? " · Chuyên viên: " + specialistReason : "")
                        + (toCoordinator ? " · Điều phối viên: " + coordinatorReason : ""));
        log.info("Booking ID={} sent back to {}", updated.getId(), request.getTo());
        return bookingQueryService.mapToResponse(updated);
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    @Override
    @Transactional
    public BookingResponseDto resubmitDocuments(String bookingIdentifier, String customerUsername) {
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

        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);
        String medicalStatus = BookingTripRules.getMedicalStatus(payload);
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

        BookingHistory.appendWithDefaultActor(payload, customerUsername != null ? customerUsername : "Khách hàng",
                "Bổ sung hồ sơ");
        savePayload(booking, payload);

        Booking updated = bookingRepository.save(booking);
        staffNotifier.toAssignedSpecialist(updated, payload, "Khách đã bổ sung hồ sơ · " + updated.getCode(),
                "Khách gửi lại hồ sơ ngựa theo yêu cầu. Vui lòng thẩm định lại.", "/specialist/verification/" + updated.getCode());
        logAction("RESUBMIT_DOCUMENTS", updated.getId(),
                "Khách hàng (" + customerUsername + ") gửi lại hồ sơ cho Chuyên viên kiểm dịch duyệt lại");
        log.info("Booking ID={} resubmitted documents by {}", updated.getId(), customerUsername);
        return bookingQueryService.mapToResponse(updated);
    }

    // Khoang đơn mở rộng chiếm 2 ngăn, khoang tiêu chuẩn chiếm 1 ngăn.
    private static int stallsNeeded(List<BookingHorseDto> horses, List<String> horseIds) {
        Map<String, BookingHorseDto> byId = new HashMap<>();
        if (horses != null) {
            horses.stream().filter(h -> h.getHorseId() != null).forEach(h -> byId.putIfAbsent(h.getHorseId(), h));
        }
        return horseIds.stream().mapToInt(id -> {
            BookingHorseDto horse = byId.get(id);
            if (horse == null) return 1;
            BookingServiceSelectionDto stall = horse.getSelectedServices() != null
                    ? horse.getSelectedServices().get("stall")
                    : null;
            boolean extended = (stall != null && "STALL_SINGLE_EXTENDED".equalsIgnoreCase(stall.getServiceCode()))
                    || "single".equalsIgnoreCase(horse.getStall());
            return extended ? 2 : 1;
        }).sum();
    }

    @Override
    @Transactional
    public BookingResponseDto sendQuote(String bookingIdentifier, SendQuoteRequestDto request) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));
        BookingSchedulePolicy.requireCurrentSchedule(booking, clock.instant());

        if (!"pending_commercial".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Đơn hàng không ở bước duyệt báo giá (pending_commercial)!");
        }

        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);
        String medicalStatus = BookingTripRules.getMedicalStatus(payload);
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

        HorseInsurancePolicy.requireQuoteReady(payload.getHorses());
        com.example.racehorse_transport.util.QuoteLinePolicy.requireValid(request.getLines(), payload.getHorses());
        List<QuoteLineDto> canonicalLines = new ArrayList<>(request.getLines() != null ? request.getLines() : List.of());
        canonicalLines.removeIf(line -> line.getLabel() != null && line.getLabel().toLowerCase(Locale.ROOT).contains("bảo hiểm"));
        canonicalLines.addAll(HorseInsurancePolicy.quoteLines(payload.getHorses()));
        request.setLines(canonicalLines);
        // Tổng, cọc, số dư do backend tính từ các dòng và khoản điều chỉnh; bỏ qua các con số tổng client gửi
        BigDecimal depositRate = new BigDecimal(systemConfigService.getConfig("DEPOSIT_RATE").getConfigValue());
        int quoteValidHours = Integer.parseInt(systemConfigService.getConfig("QUOTE_VALID_HOURS").getConfigValue());
        QuoteTotals totals = QuoteTotals.of(request.getLines(), request.getAdjustments(), depositRate);
        BigDecimal insuranceTotal = HorseInsurancePolicy.quoteLines(payload.getHorses()).stream()
            .map(QuoteLineDto::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totals.total().compareTo(insuranceTotal) < 0) throw new IllegalArgumentException("Điều chỉnh vận chuyển không được giảm vào phí thu hộ bảo hiểm.");
        BigDecimal total = totals.total();
        BigDecimal deposit = totals.deposit();
        BigDecimal balance = totals.balance();

        Instant now = clock.instant();
        Instant expiresAt = now.plus(quoteValidHours, ChronoUnit.HOURS);
        Instant scheduleDeadline = BookingSchedulePolicy.deadline(booking);
        if (scheduleDeadline != null && scheduleDeadline.isBefore(expiresAt)) expiresAt = scheduleDeadline;
        for (var horse : payload.getHorses()) {
            var insurance = horse.getInsurance();
            if (insurance != null && "assistance".equals(insurance.getOption()) && !HorseInsurancePolicy.blank(insurance.getQuoteValidUntil())) {
                Instant limit = java.time.LocalDate.parse(insurance.getQuoteValidUntil()).plusDays(1)
                    .atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().minusMillis(1);
                if (limit.isBefore(expiresAt)) expiresAt = limit;
            }
        }

        User sentByUser = null;
        if (request.getBy() != null && !request.getBy().isBlank()) {
            sentByUser = userRepository.findByUsernameOrEmail(request.getBy(), request.getBy()).orElse(null);
        }

        String linesJson = null;
        try {
            Map<String, Object> quoteContent = new HashMap<>();
            quoteContent.put("insuranceSnapshot", payload.getHorses());
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
        quoteMap.put("insuranceSnapshot", objectMapper.convertValue(payload.getHorses(), List.class));
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

        BookingHistory.appendWithDefaultActor(payload, request.getBy() != null ? request.getBy() : "Quản lý",
                "Duyệt và phát hành báo giá");
        savePayload(booking, payload);

        booking.setStatus("awaiting_payment");
        Booking updated = bookingRepository.save(booking);

        String bookingCode = updated.getCode();
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

        billMailService.quoteIssued(updated, request.getLines(), total, deposit, balance, expiresAt);
        logAction("SEND_QUOTATION", updated.getId(), "Phát hành báo giá đơn " + updated.getCode()
                + " (Tổng: " + total + ", Cọc: " + deposit + ", Hết hạn: " + expiresAt + ")");

        log.info("Successfully issued quote for booking ID={}, status=awaiting_payment", updated.getId());
        return bookingQueryService.mapToResponse(updated);
    }

    @Override
    @Transactional
    public BookingResponseDto rejectQuote(String bookingIdentifier, String customerUsername,
            RejectQuoteRequestDto request) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
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
        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);

        String reason = request != null && request.getReason() != null ? request.getReason().trim()
                : "Khách hàng không đồng ý báo giá";
        BookingHistory.appendWithDefaultActor(payload, customerUsername != null ? customerUsername : "Khách hàng",
                "Từ chối báo giá");
        savePayload(booking, payload);
        staffNotifier.bookingCancelled(booking, payload, "khách từ chối báo giá");

        Booking updated = bookingRepository.save(booking);
        logAction("REJECT_QUOTE", updated.getId(),
                "Khách hàng từ chối báo giá đơn " + updated.getCode() + ": " + reason);
        log.info("Booking ID={} cancelled by customer rejecting quote", updated.getId());
        return bookingQueryService.mapToResponse(updated);
    }

    // Báo khách khi báo giá hết hạn để họ biết đơn không còn giữ chỗ
    private void notifyQuoteExpired(Booking booking, Instant now) {
        if (booking.getCustomerID() == null || booking.getCustomerID().getUser() == null) return;
        String bookingCode = booking.getCode();
        notificationRepository.save(Notification.builder()
                .recipientRole("customer")
                .recipientUsername(booking.getCustomerID().getUser().getUsername())
                .title("Báo giá đã hết hạn · " + bookingCode)
                .message("Báo giá của đơn " + bookingCode + " đã hết hạn đặt cọc nên xe và kíp xe không còn được giữ. "
                        + "Bạn có thể tạo yêu cầu mới nếu vẫn cần vận chuyển.")
                .link("/orders/" + bookingCode)
                .bookingId(bookingCode)
                .isRead(false)
                .createdAt(now)
                .build());
    }

    @Override
    @Transactional
    public int releaseExpiredQuotations() {
        List<Integer> awaitingIds = bookingRepository.findIdsByStatusOrderByBookingDateAsc("awaiting_payment");
        int count = 0;

        for (Integer bookingId : awaitingIds) {
            Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
            // Có thể đã nhận cọc trong lúc chờ khóa: luôn kiểm tra lại trạng thái vừa đọc.
            if (booking == null || !"awaiting_payment".equalsIgnoreCase(booking.getStatus())) continue;
            Instant now = clock.instant();
            Optional<Quotation> quoteOpt = quotationRepository.findTopByBookingIdOrderBySentAtDesc(booking.getId());
            if (quoteOpt.isPresent()) {
                Quotation q = quoteOpt.get();
                if (q.getExpiresAt() != null && "PENDING".equalsIgnoreCase(q.getStatus())
                        && !now.isBefore(DepositPaymentWindow.expiresAt(q))) {
                    List<Payment> payments = paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(bookingId);
                    if (payments.stream().anyMatch(p -> "DEPOSIT".equalsIgnoreCase(p.getPaymentType())
                            && "COMPLETED".equalsIgnoreCase(p.getStatus()))) continue;
                    boolean pending = DepositPaymentWindow.hasPendingDeposit(payments, q);
                    if (pending && now.isBefore(DepositPaymentWindow.confirmationUntil(q,
                            vnpayProperties.getDepositConfirmationGraceMinutes()))) continue;

                    // Mất thông báo không đồng nghĩa thanh toán thất bại. Giữ PENDING để đối soát callback muộn.
                    if (pending) {
                        for (Payment p : payments) {
                            if (DepositPaymentWindow.hasPendingDeposit(List.of(p), q)) {
                                p.setNote("Hết thời gian giữ chỗ. Chưa nhận kết quả VNPAY. Chờ đối soát, chưa xác định thanh toán thất bại.");
                                paymentRepository.save(p);
                            }
                        }
                    }
                    bookingVehicleRepository.deleteByBookingID_Id(booking.getId());
                    bookingAssignmentRepository.deleteByBookingID_Id(booking.getId());

                    q.setStatus("EXPIRED");
                    quotationRepository.save(q);

                    booking.setStatus("quote_expired");
                    BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);
                    BookingHistory.appendWithDefaultActor(payload, "Hệ thống",
                            "Báo giá hết hạn");
                    savePayload(booking, payload);
                    bookingRepository.save(booking);
                    notifyQuoteExpired(booking, now);

                    logAction("QUOTE_AUTO_EXPIRED_RELEASE_FLEET", booking.getId(),
                            "Báo giá đơn " + booking.getCode()
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

    // Áp hiệu ứng sau khi đã nhận đủ tiền cọc (luồng VNPAY): chốt báo giá, cấp Vận đơn
    @Override
    @Transactional
    public Booking confirmDepositPaid(Payment payment, String actor) {
        Booking booking = payment.getBookingID();
        Instant paidAt = payment.getPaymentDate() != null ? payment.getPaymentDate() : clock.instant();

        quotationRepository.findTopByBookingIdOrderBySentAtDesc(booking.getId()).ifPresent(quotation -> {
            quotation.setStatus("ACCEPTED");
            quotationRepository.save(quotation);
        });

        // Năm của vận đơn theo ngày nhận cọc (giờ Việt Nam), phần sau là mã đơn ngẫu nhiên
        int waybillYear = paidAt.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).getYear();
        String waybillNo = "VD-" + waybillYear + "-" + booking.getCode().substring(3);
        booking.setWaybillNo(waybillNo);
        booking.setStatus("waybill_issued");

        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);
        Map<String, Object> paymentMap = new HashMap<>();
        paymentMap.put("paidAt", paidAt.toEpochMilli());
        paymentMap.put("amount", payment.getAmount());
        paymentMap.put("reference", payment.getTransactionCode());
        paymentMap.put("method", payment.getPaymentMethod());
        payload.setPayment(paymentMap);

        // Kéo tự động các quy định pháp lý tương ứng từ bảng REGULATION theo cửa khẩu của đơn
        initClearanceRegulationsForBooking(booking, payload);

        BookingHistory.appendWithDefaultActor(payload, actor != null ? actor : "Khách hàng",
                "Đặt cọc");
        savePayload(booking, payload);
        Booking updated = bookingRepository.save(booking);

        logAction("PAY_DEPOSIT", updated.getId(),
                "Khách hàng thanh toán tiền cọc (" + payment.getAmount() + " VND) qua " + payment.getPaymentMethod()
                        + ", mã GD " + payment.getTransactionCode() + ", cấp Vận đơn " + waybillNo);
        log.info("Deposit confirmed for booking ID={}, waybill={}", updated.getId(), waybillNo);
        String depositCode = updated.getCode();
        staffNotifier.toManagers(updated, "Khách đã đặt cọc · " + depositCode, "Đơn " + depositCode + " đã có vận đơn " + waybillNo + ".", "/manager");
        staffNotifier.toAssignedSpecialist(updated, payload, "Khách đã đặt cọc · " + depositCode,
                "Xác nhận hợp đồng bảo hiểm (nếu khách nhờ mua) và làm giấy tờ chuyến đi.", "/specialist/legal/" + depositCode);
        staffNotifier.toCrew(updated, payload, "Bạn được phân công chuyến · " + depositCode,
                "Khách đã đặt cọc, chuyến " + depositCode + " được chốt. Theo dõi giấy tờ và lệnh điều xe trên ứng dụng.");
        billMailService.paymentReceived(updated, BillMailService.Payment.DEPOSIT, payment.getAmount(), payment.getGatewayTransactionNo(), paidAt, waybillNo);
        return updated;
    }

    private void initClearanceRegulationsForBooking(Booking booking, BookingDetailsPayloadDto payload) {
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

        // 2. Dựng clearance.items từ cấu hình giấy tờ (dòng chung + dòng riêng theo cửa khẩu)
        String configuredDestinationCountry = payload.getDestCountry() != null ? payload.getDestCountry()
                : borderGate != null ? borderGate.getDestinationCountry() : null;
        List<Map<String, Object>> items = ClearanceDocumentPolicy.buildItems(
                documentRequirementRepository.findByDocumentGroupAndStatusOrderByDocumentNameAsc("CLEARANCE", "ACTIVE"),
                isInternational, borderGate, configuredDestinationCountry, payload.getDocumentRequirements());

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

        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);
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

        BookingHistory.appendWithDefaultActor(payload, actor != null ? actor : "Khách hàng",
                "Thanh toán phần còn lại");
        savePayload(booking, payload);
        Booking updated = bookingRepository.save(booking);

        staffNotifier.toCrew(updated, payload, "Khách đã trả 70% · " + updated.getCode(),
                "Khách đã thanh toán phần còn lại. Xe được bắt đầu hành trình khi đủ các bước ở điểm đón.");
        logAction("PAY_BALANCE", updated.getId(),
                "Khách hàng thanh toán 70% còn lại (" + payment.getAmount() + " VND) qua " + payment.getPaymentMethod()
                        + ", mã GD " + payment.getTransactionCode() + " cho đơn " + updated.getCode());
        log.info("Balance confirmed for booking ID={}", updated.getId());
        billMailService.paymentReceived(updated, BillMailService.Payment.BALANCE, payment.getAmount(), payment.getGatewayTransactionNo(), paidAt, null);
        return updated;
    }

    private Integer parseBookingId(String bookingIdentifier) {
        if (bookingIdentifier == null || bookingIdentifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã đơn hàng không được để trống!");
        }
        return bookingRepository.findIdByCode(bookingIdentifier.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));
    }

    private void logAction(String actionType, Integer targetRecordId, String newData) {
        try {
            operationalLog.record(actionType, "BOOKING", targetRecordId, newData, null);
        } catch (Exception ex) {
            log.warn("Failed to write operational log for action {}: {}", actionType, ex.getMessage());
        }
    }

    private void savePayload(Booking booking, BookingDetailsPayloadDto payload) {
        try {
            booking.setDetailsJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không thể lưu chi tiết đơn " + booking.getId() + ".", ex);
        }
    }

    @Override
    public List<BookingResponseDto> getCustomerBookings(String customerUsername) {
        return bookingQueryService.getCustomerBookings(customerUsername);
    }

    @Override
    public PagedResponseDto<BookingResponseDto> getCustomerBookingsPaged(String customerUsername, int page, int size) {
        return bookingQueryService.getCustomerBookingsPaged(customerUsername, page, size);
    }

    @Override
    public BookingResponseDto getBookingById(Integer id, String customerUsername) {
        return bookingQueryService.getBookingById(id, customerUsername);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerTripTeamResponseDto> getCustomerTripTeam(Integer id, String customerUsername) {
        return bookingQueryService.getCustomerTripTeam(id, customerUsername);
    }

    @Override
    public List<BookingResponseDto> getBookingsByStatus(String status) {
        return bookingQueryService.getBookingsByStatus(status);
    }

    @Override
    public PagedResponseDto<BookingResponseDto> getBookingsPaged(String status, int page, int size) {
        return bookingQueryService.getBookingsPaged(status, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponseDto> getMyFieldBookings(String username, String role) {
        return bookingQueryService.getMyFieldBookings(username, role);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponseDto> getAssignedBookings(String username) {
        return bookingQueryService.getAssignedBookings(username);
    }

    @Override
    @Transactional
    public int markOverdueSettlements() {
        // Company expenses never create customer payment deadlines.
        return 0;
    }

    @Override
    public boolean isPickupComplete(Booking booking) {
        return tripProgressService.isPickupComplete(booking);
    }

    @Override
    @Transactional
    public BookingResponseDto updateTripProgress(String bookingIdentifier, TripUpdateRequestDto request,
            String username, String role) {
        return tripProgressService.updateTripProgress(bookingIdentifier, request, username, role);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicTrackingResponseDto getPublicTracking(String code, String phoneLast4) {
        return bookingQueryService.getPublicTracking(code, phoneLast4);
    }
}
