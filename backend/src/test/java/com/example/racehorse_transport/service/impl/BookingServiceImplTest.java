package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.dto.booking.BookingServiceSelectionDto;
import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.booking.CreateBookingRequest;
import com.example.racehorse_transport.dto.booking.CustomerTripTeamResponse;
import com.example.racehorse_transport.dto.booking.PartyContactDto;
import com.example.racehorse_transport.dto.booking.RejectQuoteRequest;
import com.example.racehorse_transport.dto.manager.RejectBookingRequest;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Category;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.DocumentRequirement;
import com.example.racehorse_transport.entity.Notification;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.dto.admin.SystemConfigResponse;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingAssignmentRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingVehicleRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.repository.HorseDocumentRepository;
import com.example.racehorse_transport.repository.DocumentRequirementRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.LogRepository;
import com.example.racehorse_transport.repository.NotificationRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.repository.RegulationRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.ServiceRepository;
import com.example.racehorse_transport.repository.SystemLogRepository;
import com.example.racehorse_transport.repository.TripDocumentRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.service.FleetService;
import com.example.racehorse_transport.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.Clock;
import com.example.racehorse_transport.config.VnpayProperties;
import com.example.racehorse_transport.entity.Payment;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceImplTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final HorseRepository horseRepository = mock(HorseRepository.class);
    private final SystemLogRepository systemLogRepository = mock(SystemLogRepository.class);
    private final BookingVehicleRepository bookingVehicleRepository = mock(BookingVehicleRepository.class);
    private final BookingAssignmentRepository bookingAssignmentRepository = mock(BookingAssignmentRepository.class);
    private final QuotationRepository quotationRepository = mock(QuotationRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final FleetService fleetService = mock(FleetService.class);
    private final LogRepository logRepository = mock(LogRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final StaffRepository staffRepository = mock(StaffRepository.class);
    private final RegulationRepository regulationRepository = mock(RegulationRepository.class);
    private final LocationRepository locationRepository = mock(LocationRepository.class);
    private final TripDocumentRepository tripDocumentRepository = mock(TripDocumentRepository.class);
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final com.example.racehorse_transport.repository.RouteRepository routeRepository = mock(com.example.racehorse_transport.repository.RouteRepository.class);
    private final ServiceRepository serviceRepository = mock(ServiceRepository.class);
    private final HorseDocumentRepository horseDocumentRepository = mock(HorseDocumentRepository.class);
    private final DocumentRequirementRepository documentRequirementRepository = mock(DocumentRequirementRepository.class);

    private final com.example.racehorse_transport.repository.HorseJourneyLogRepository horseJourneyLogRepository =
            mock(com.example.racehorse_transport.repository.HorseJourneyLogRepository.class);
    private final com.example.racehorse_transport.service.HorseDeliveryLocationService horseDeliveryLocationService =
            new com.example.racehorse_transport.service.HorseDeliveryLocationService(horseRepository, horseJourneyLogRepository);
    private final com.example.racehorse_transport.service.BillMailService billMailService = mock(com.example.racehorse_transport.service.BillMailService.class);
    private BookingServiceImpl service;
    private Customer customer;
    private final Clock clock = mock(Clock.class);
    private static final Instant DEPOSIT_DEADLINE = Instant.parse("2026-11-07T03:00:00Z");

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(DEPOSIT_DEADLINE);
        service = new BookingServiceImpl(
                mock(com.example.racehorse_transport.service.CaptureService.class),
                bookingRepository,
                userRepository,
                customerRepository,
                horseRepository,
                systemLogRepository,
                bookingVehicleRepository,
                bookingAssignmentRepository,
                quotationRepository,
                notificationRepository,
                paymentRepository,
                fleetService,
                logRepository,
                vehicleRepository,
                staffRepository,
                regulationRepository,
                locationRepository,
                systemConfigService,
                tripDocumentRepository,
                routeRepository,
                serviceRepository,
                horseDocumentRepository,
                documentRequirementRepository,
                horseDeliveryLocationService,
                new VnpayProperties(),
                clock,
                mock(com.example.racehorse_transport.repository.IncidentRepository.class),
                billMailService, org.mockito.Mockito.mock(com.example.racehorse_transport.service.StaffNotifier.class));

        when(documentRequirementRepository.findByDocumentGroupAndStatusOrderByDocumentNameAsc(any(), any()))
                .thenReturn(List.of());
        when(documentRequirementRepository.findByStatusOrderByDocumentGroupAscDocumentNameAsc(any()))
                .thenReturn(List.of());
        when(horseDocumentRepository.findByHorseID_Id(any())).thenReturn(List.of());

        when(systemConfigService.getConfig("MIN_LEAD_DAYS")).thenReturn(config("MIN_LEAD_DAYS", "20"));
        when(systemConfigService.getConfig("DEPOSIT_RATE")).thenReturn(config("DEPOSIT_RATE", "0.30"));
        when(systemConfigService.getConfig("QUOTE_VALID_HOURS")).thenReturn(config("QUOTE_VALID_HOURS", "24"));

        User user = new User();
        user.setId(7);
        user.setUsername("phuc");
        user.setEmail("phuc@example.com");
        customer = new Customer();
        customer.setId(7);
        customer.setUser(user);
        customer.setFullName("Phuc");
    }

    private SystemConfigResponse config(String key, String value) {
        return SystemConfigResponse.builder().configKey(key).configValue(value).build();
    }

    @Test
    void createBooking_savesPendingBookingAndAuditLog() {
        Instant departure = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                .plusDays(25).atTime(10, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        BookingHorseDto horseDto = BookingHorseDto.builder()
                .insurance(com.example.racehorse_transport.dto.booking.HorseInsuranceDto.builder().option("none").quoteValidUntil("2027-01-01").policyDocuments(List.of("https://example.test/forged.pdf")).build())
                .horseId("HORSE-12")
                .name("Lightning")
                .selectedServiceCodes(Map.of(
                        "stall", "STALL_STANDARD",
                        "feed", "FEED_BASIC",
                        "insurance", "INSURANCE_DECLINED"))
                .build();
        CreateBookingRequest request = CreateBookingRequest.builder()
                .type("INTERNATIONAL")
                .originName("  Hanoi  ")
                .originCountry("VN")
                .destName("  Tokyo  ")
                .destCountry("JP")
                .departAt(departure.toEpochMilli())
                .consignor(PartyContactDto.builder().name("  Sender  ").phone(" 0900 ").build())
                .consignee(PartyContactDto.builder().name(" Receiver ").phone(" 0901 ").build())
                .horses(List.of(horseDto))
                .build();
        Horse horse = Horse.builder().id(12).customerID(customer).currentCountry("VN").build();

        when(userRepository.findByUsernameOrEmail("phuc", "phuc")).thenReturn(Optional.of(customer.getUser()));
        when(customerRepository.findById(7)).thenReturn(Optional.of(customer));
        when(bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(7)).thenReturn(List.of());
        when(horseRepository.findById(12)).thenReturn(Optional.of(horse));
        when(serviceRepository.findByServiceCodeIgnoreCase("STALL_STANDARD"))
                .thenReturn(Optional.of(service(1, "Khoang tiêu chuẩn", 1, "1", "ACTIVE")));
        when(serviceRepository.findByServiceCodeIgnoreCase("FEED_BASIC"))
                .thenReturn(Optional.of(service(3, "Gói cơ bản", 2, "0", "ACTIVE")));
        DocumentRequirement clearanceRequirement = new DocumentRequirement();
        clearanceRequirement.setDocumentGroup("CLEARANCE");
        clearanceRequirement.setDocumentType("poa");
        clearanceRequirement.setDocumentName("Giấy ủy quyền vận chuyển");
        clearanceRequirement.setTripType("ALL");
        clearanceRequirement.setCountry("ALL");
        clearanceRequirement.setRequired(true);
        clearanceRequirement.setStatus("ACTIVE");
        when(documentRequirementRepository.findByStatusOrderByDocumentGroupAscDocumentNameAsc("ACTIVE"))
                .thenReturn(List.of(clearanceRequirement));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking saved = invocation.getArgument(0);
            saved.setId(42);
            return saved;
        });

        BookingResponse response = service.createBooking("phuc", request);

        assertThat(response.getId()).isEqualTo("BK-042");
        assertThat(response.getNumericId()).isEqualTo(42);
        assertThat(response.getType()).isEqualTo("international");
        assertThat(response.getStatus()).isEqualTo("pending_intake");
        assertThat(response.getDepartAt()).isEqualTo(departure.toEpochMilli());
        assertThat(response.getOriginName()).isEqualTo("Hanoi");
        assertThat(response.getDestName()).isEqualTo("Tokyo");
        assertThat(response.getTotalHorses()).isEqualTo(1);
        assertThat(response.getHorses()).hasSize(1);
        assertThat(response.getHorses().get(0).getHorseId()).isEqualTo("HORSE-12");
        assertThat(response.getHorses().get(0).getName()).isEqualTo("Lightning");
        assertThat(response.getHorses().get(0).getSelectedServices().get("stall").getPrice())
                .isEqualByComparingTo("1");
        assertThat(response.getHorses().get(0).getSelectedServiceCodes().get("feed")).isEqualTo("FEED_BASIC");
        assertThat(response.getHorses().get(0).getSelectedServices().get("feed").getBillingUnit())
                .isEqualTo("PER_HORSE_DAY");
        assertThat(response.getHorses().get(0).getInsuranceOpted()).isFalse();
        assertThat(response.getHorses().get(0).getInsurance().getPolicyDocuments()).isNull();
        assertThat(response.getHorses().get(0).getInsurance().getQuoteValidUntil()).isNull();

        ArgumentCaptor<Booking> savedBooking = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(savedBooking.capture());
        assertThat(savedBooking.getValue().getConsignorName()).isEqualTo("Sender");
        assertThat(savedBooking.getValue().getConsignorPhone()).isEqualTo("0900");
        assertThat(savedBooking.getValue().getDetailsJson()).contains("\"originCountry\":\"VN\"");
        assertThat(savedBooking.getValue().getDetailsJson()).contains("\"serviceName\":\"Khoang tiêu chuẩn\"");
        assertThat(savedBooking.getValue().getDetailsJson()).contains("\"documentType\":\"poa\"");
        verify(systemLogRepository).save(any());
    }

    @Test
    void createBooking_rejectsHorseLocatedInDifferentOriginCountry() {
        Instant departure = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                .plusDays(25).atTime(10, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        BookingHorseDto horseDto = BookingHorseDto.builder()
                .insurance(com.example.racehorse_transport.dto.booking.HorseInsuranceDto.builder().option("none").build())
                .horseId("HORSE-12")
                .name("Lightning")
                .selectedServiceCodes(Map.of(
                        "stall", "STALL_STANDARD",
                        "feed", "FEED_BASIC",
                        "insurance", "INSURANCE_DECLINED"))
                .build();
        CreateBookingRequest request = CreateBookingRequest.builder()
                .type("INTERNATIONAL")
                .originName("Trang trại tại Việt Nam")
                .originCountry("VN")
                .destName("Điểm giao tại Lào")
                .destCountry("LA")
                .departAt(departure.toEpochMilli())
                .consignor(PartyContactDto.builder().name("Sender").phone("0900").build())
                .consignee(PartyContactDto.builder().name("Receiver").phone("0901").build())
                .horses(List.of(horseDto))
                .build();
        Horse horse = Horse.builder()
                .id(12)
                .customerID(customer)
                .horseName("Lightning")
                .currentCountry("KH")
                .build();

        when(userRepository.findByUsernameOrEmail("phuc", "phuc")).thenReturn(Optional.of(customer.getUser()));
        when(customerRepository.findById(7)).thenReturn(Optional.of(customer));
        when(bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(7)).thenReturn(List.of());
        when(horseRepository.findById(12)).thenReturn(Optional.of(horse));
        when(serviceRepository.findByServiceCodeIgnoreCase("STALL_STANDARD"))
                .thenReturn(Optional.of(service(1, "Khoang tiêu chuẩn", 1, "1", "ACTIVE")));
        when(serviceRepository.findByServiceCodeIgnoreCase("FEED_BASIC"))
                .thenReturn(Optional.of(service(3, "Gói cơ bản", 2, "0", "ACTIVE")));

        assertThatThrownBy(() -> service.createBooking("phuc", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hiện ở KH")
                .hasMessageContaining("điểm đón ở VN");
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void createBooking_rejectsHorseWithLocationButUnknownCountry() {
        Instant departure = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                .plusDays(25).atTime(10, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        BookingHorseDto horseDto = BookingHorseDto.builder()
                .insurance(com.example.racehorse_transport.dto.booking.HorseInsuranceDto.builder().option("none").build())
                .horseId("HORSE-12")
                .name("Lightning")
                .selectedServiceCodes(Map.of(
                        "stall", "STALL_STANDARD",
                        "feed", "FEED_BASIC",
                        "insurance", "INSURANCE_DECLINED"))
                .build();
        CreateBookingRequest request = CreateBookingRequest.builder()
                .type("INTERNATIONAL")
                .originName("Trang trại tại Việt Nam")
                .originCountry("VN")
                .destName("Điểm giao tại Lào")
                .destCountry("LA")
                .departAt(departure.toEpochMilli())
                .consignor(PartyContactDto.builder().name("Sender").phone("0900").build())
                .consignee(PartyContactDto.builder().name("Receiver").phone("0901").build())
                .horses(List.of(horseDto))
                .build();
        Horse horse = Horse.builder()
                .id(12)
                .customerID(customer)
                .horseName("Lightning")
                .currentLocationName("Phnom Penh")
                .build();

        when(userRepository.findByUsernameOrEmail("phuc", "phuc")).thenReturn(Optional.of(customer.getUser()));
        when(customerRepository.findById(7)).thenReturn(Optional.of(customer));
        when(bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(7)).thenReturn(List.of());
        when(horseRepository.findById(12)).thenReturn(Optional.of(horse));
        when(serviceRepository.findByServiceCodeIgnoreCase("STALL_STANDARD"))
                .thenReturn(Optional.of(service(1, "Khoang tiêu chuẩn", 1, "1", "ACTIVE")));
        when(serviceRepository.findByServiceCodeIgnoreCase("FEED_BASIC"))
                .thenReturn(Optional.of(service(3, "Gói cơ bản", 2, "0", "ACTIVE")));

        assertThatThrownBy(() -> service.createBooking("phuc", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chưa xác định quốc gia")
                .hasMessageContaining("vị trí chuồng");
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void createBooking_rejectsServiceThatWasPausedAfterCatalogLoad() {
        Instant departure = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                .plusDays(25).atTime(10, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        BookingHorseDto horseDto = BookingHorseDto.builder()
                .insurance(com.example.racehorse_transport.dto.booking.HorseInsuranceDto.builder().option("none").build())
                .horseId("HORSE-12")
                .name("Lightning")
                .selectedServices(Map.of(
                        "stall", serviceSelection(1),
                        "feed", serviceSelection(3),
                        "water", serviceSelection(6),
                        "insurance", serviceSelection(10)))
                .build();
        CreateBookingRequest request = CreateBookingRequest.builder()
                .type("DOMESTIC")
                .departAt(departure.toEpochMilli())
                .horses(List.of(horseDto))
                .build();
        when(userRepository.findByUsernameOrEmail("phuc", "phuc")).thenReturn(Optional.of(customer.getUser()));
        when(customerRepository.findById(7)).thenReturn(Optional.of(customer));
        when(bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(7)).thenReturn(List.of());
        when(serviceRepository.findById(1)).thenReturn(Optional.of(service(1, "Khoang tiêu chuẩn", 1, "1000000", "INACTIVE")));
        when(serviceRepository.findById(3)).thenReturn(Optional.of(service(3, "Gói cơ bản", 2, "0", "ACTIVE")));
        when(serviceRepository.findById(6)).thenReturn(Optional.of(service(6, "Mỗi 3 giờ", 3, "0", "ACTIVE")));
        when(serviceRepository.findById(10)).thenReturn(Optional.of(service(10, "Từ chối", 4, "0", "ACTIVE")));

        assertThatThrownBy(() -> service.createBooking("phuc", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vừa được tạm ngưng");
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void createBooking_rejectsHorseWithoutApprovedRequiredDocument() {
        Instant departure = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                .plusDays(25).atTime(10, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        BookingHorseDto horseDto = BookingHorseDto.builder()
                .insurance(com.example.racehorse_transport.dto.booking.HorseInsuranceDto.builder().option("none").build())
                .horseId("HORSE-12")
                .name("Lightning")
                .selectedServiceCodes(Map.of(
                        "stall", "STALL_STANDARD",
                        "feed", "FEED_BASIC",
                        "insurance", "INSURANCE_DECLINED"))
                .build();
        CreateBookingRequest request = CreateBookingRequest.builder()
                .type("DOMESTIC")
                .originCountry("VN")
                .destCountry("VN")
                .departAt(departure.toEpochMilli())
                .horses(List.of(horseDto))
                .build();
        Horse horse = Horse.builder().id(12).customerID(customer).horseName("Lightning").build();
        DocumentRequirement requirement = new DocumentRequirement();
        requirement.setDocumentGroup("HORSE");
        requirement.setDocumentType("passport");
        requirement.setDocumentName("Hộ chiếu ngựa");
        requirement.setTripType("ALL");
        requirement.setCountry("ALL");
        requirement.setRequired(true);
        requirement.setStatus("ACTIVE");

        when(userRepository.findByUsernameOrEmail("phuc", "phuc")).thenReturn(Optional.of(customer.getUser()));
        when(customerRepository.findById(7)).thenReturn(Optional.of(customer));
        when(bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(7)).thenReturn(List.of());
        when(horseRepository.findById(12)).thenReturn(Optional.of(horse));
        when(serviceRepository.findByServiceCodeIgnoreCase("STALL_STANDARD"))
                .thenReturn(Optional.of(service(1, "Khoang tiêu chuẩn", 1, "1", "ACTIVE")));
        when(serviceRepository.findByServiceCodeIgnoreCase("FEED_BASIC"))
                .thenReturn(Optional.of(service(3, "Gói cơ bản", 2, "0", "ACTIVE")));
        when(documentRequirementRepository.findByStatusOrderByDocumentGroupAscDocumentNameAsc("ACTIVE"))
                .thenReturn(List.of(requirement));
        when(horseDocumentRepository.findByHorseID_Id(12)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createBooking("phuc", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hộ chiếu ngựa");
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    private BookingServiceSelectionDto serviceSelection(int id) {
        return BookingServiceSelectionDto.builder().id(id).build();
    }

    private com.example.racehorse_transport.entity.Service service(
            int id, String name, int categoryId, String price, String status) {
        Category category = new Category();
        category.setId(categoryId);
        category.setCategoryName("Category " + categoryId);
        com.example.racehorse_transport.entity.Service item = new com.example.racehorse_transport.entity.Service();
        item.setId(id);
        item.setServiceName(name);
        item.setServiceCode(switch (id) {
            case 1 -> "STALL_STANDARD";
            case 3 -> "FEED_BASIC";
            case 6 -> "WATER_EVERY_3H";
            case 10 -> "INSURANCE_DECLINED";
            default -> "SERVICE_" + id;
        });
        item.setBillingUnit(categoryId == 2 ? "PER_HORSE_DAY" : "PER_HORSE");
        item.setCategoryID(category);
        item.setPrice(new java.math.BigDecimal(price));
        item.setStatus(status);
        return item;
    }

    @Test
    void createBooking_rejectsDepartureBeforeConfiguredLeadTime() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .departAt(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                        .plusDays(19).atTime(10, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                        .toInstant().toEpochMilli())
                .build();
        when(userRepository.findByUsernameOrEmail("phuc", "phuc")).thenReturn(Optional.of(customer.getUser()));
        when(customerRepository.findById(7)).thenReturn(Optional.of(customer));
        when(bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(7)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createBooking("phuc", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tối thiểu 20 ngày");

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void createBooking_rejectsCustomerWithOverdueSettlement() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .consignor(PartyContactDto.builder().name("Sender").phone("0900").build())
                .build();
        Booking overdueBooking = Booking.builder().status("PAYMENT_OVERDUE").build();

        when(userRepository.findByUsernameOrEmail("phuc", "phuc")).thenReturn(Optional.of(customer.getUser()));
        when(customerRepository.findById(7)).thenReturn(Optional.of(customer));
        when(bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(7)).thenReturn(List.of(overdueBooking));

        assertThatThrownBy(() -> service.createBooking("phuc", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payment_overdue");

        verify(bookingRepository, never()).save(any(Booking.class));
        verify(systemLogRepository, never()).save(any());
        verify(horseRepository, never()).findById(any(Integer.class));
    }

    @Test
    void createBooking_rejectsHorseInTransit() {
        BookingHorseDto horseDto = BookingHorseDto.builder()
                .insurance(com.example.racehorse_transport.dto.booking.HorseInsuranceDto.builder().option("none").build()).horseId("HORSE-12").name("Lightning").build();
        CreateBookingRequest request = CreateBookingRequest.builder()
                .horses(List.of(horseDto))
                .consignor(PartyContactDto.builder().name("Sender").phone("0900").build())
                .build();
        Horse horse = Horse.builder()
                .id(12)
                .horseName("Lightning")
                .customerID(customer)
                .transitStatus("IN_TRANSIT")
                .build();

        when(userRepository.findByUsernameOrEmail("phuc", "phuc")).thenReturn(Optional.of(customer.getUser()));
        when(customerRepository.findById(7)).thenReturn(Optional.of(customer));
        when(bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(7)).thenReturn(List.of());
        when(horseRepository.findById(12)).thenReturn(Optional.of(horse));

        assertThatThrownBy(() -> service.createBooking("phuc", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IN_TRANSIT");

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void createBooking_rejectsHorseInUncompletedBooking() {
        BookingHorseDto horseDto = BookingHorseDto.builder()
                .insurance(com.example.racehorse_transport.dto.booking.HorseInsuranceDto.builder().option("none").build()).horseId("HORSE-12").name("Lightning").build();
        CreateBookingRequest request = CreateBookingRequest.builder()
                .horses(List.of(horseDto))
                .consignor(PartyContactDto.builder().name("Sender").phone("0900").build())
                .build();
        Horse horse = Horse.builder()
                .id(12)
                .horseName("Lightning")
                .customerID(customer)
                .transitStatus("STABLE_IDLE")
                .build();

        Booking existingActiveBooking = Booking.builder()
                .id(99)
                .status("in_transit")
                .detailsJson("{\"horses\":[{\"horseId\":\"HORSE-12\",\"name\":\"Lightning\"}]}")
                .build();

        when(userRepository.findByUsernameOrEmail("phuc", "phuc")).thenReturn(Optional.of(customer.getUser()));
        when(customerRepository.findById(7)).thenReturn(Optional.of(customer));
        when(bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(7)).thenReturn(List.of(existingActiveBooking));
        when(horseRepository.findById(12)).thenReturn(Optional.of(horse));

        assertThatThrownBy(() -> service.createBooking("phuc", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("#BK-99");

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void createBooking_rejectsHorseWithoutProfileId() {
        givenCustomerCanCreateBooking();

        assertThatThrownBy(() -> service.createBooking("phuc", requestWithHorseId(" ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("phải được chọn từ Hồ sơ ngựa");

        verify(horseRepository, never()).findById(any(Integer.class));
        verify(systemLogRepository, never()).save(any());
    }

    @Test
    void createBooking_rejectsMalformedHorseProfileId() {
        givenCustomerCanCreateBooking();

        assertThatThrownBy(() -> service.createBooking("phuc", requestWithHorseId("HORSE-ABC")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mã hồ sơ ngựa không hợp lệ");

        verify(horseRepository, never()).findById(any(Integer.class));
        verify(systemLogRepository, never()).save(any());
    }

    @Test
    void createBooking_rejectsHorseProfileThatDoesNotExist() {
        givenCustomerCanCreateBooking();
        when(horseRepository.findById(12)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createBooking("phuc", requestWithHorseId("HORSE-12")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy hồ sơ ngựa HORSE-12");

        verify(systemLogRepository, never()).save(any());
    }

    @Test
    void createBooking_rejectsHorseOwnedByAnotherCustomer() {
        givenCustomerCanCreateBooking();
        Customer anotherCustomer = new Customer();
        anotherCustomer.setId(8);
        when(horseRepository.findById(12))
                .thenReturn(Optional.of(Horse.builder().id(12).customerID(anotherCustomer).build()));

        assertThatThrownBy(() -> service.createBooking("phuc", requestWithHorseId("HORSE-12")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không thuộc tài khoản của bạn");

        verify(systemLogRepository, never()).save(any());
    }

    @Test
    void getBookingById_allowsOwnerUsernameOrEmail() {
        Booking booking = bookingOwnedByCustomer();
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));

        BookingResponse byUsername = service.getBookingById(42, "phuc");
        BookingResponse byEmail = service.getBookingById(42, "phuc@example.com");

        assertThat(byUsername.getNumericId()).isEqualTo(42);
        assertThat(byEmail.getNumericId()).isEqualTo(42);
    }

    @Test
    void excludeUnfitHorse_rejectsAnUnreadableDiscountInsteadOfUsingZero() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("ready_for_pickup");
        booking.setDetailsJson("{\"horses\":[{\"horseId\":\"H-001\",\"name\":\"Storm\"}],"
                + "\"trips\":[{\"tripId\":\"TRP-042-1\",\"vehicleId\":\"VH-001\",\"horseIds\":[\"H-001\"]}]}");
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        var request = new com.example.racehorse_transport.dto.field.TripUpdateRequest();
        request.setTripId("TRP-042-1");
        request.setAction("EXCLUDE_UNFIT_HORSE");
        request.setActor("escort01");
        request.setPayload(Map.of("horseId", "H-001", "discountAmount", "năm triệu"));

        assertThatThrownBy(() -> service.updateTripProgress("BK-042", request, "manager", "MANAGER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("giảm trừ");
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void updateTripProgressPersistsOriginalsReturnedOnDeliveryCheckpoint() {
        Booking booking = deliveryBooking("""
                {
                  "startedAt": 100,
                  "checkpoints": [
                    { "id": "pickup", "type": "pickup", "arrivedAt": 100, "doneAt": 101 },
                    { "id": "delivery", "type": "delivery", "arrivedAt": 200, "photo": "arrival.jpg", "handoverPhoto": "signed.jpg" }
                  ],
                  "welfare": []
                }
                """);
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new com.example.racehorse_transport.dto.field.TripUpdateRequest();
        request.setTripId("TRP-042-1");
        request.setAction("RETURN_ORIGINALS");
        request.setActor("driver02");
        request.setPayload(Map.of("checkpointId", "delivery", "returnedOriginals", true));

        service.updateTripProgress("BK-042", request, "manager", "MANAGER");

        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(saved.capture());
        assertThat(saved.getValue().getDetailsJson())
                .contains("\"returnedOriginals\":true")
                .contains("\"returnedOriginalsBy\":\"manager\"")
                .contains("\"returnedOriginalsAt\"");
    }

    @Test
    void completeDeliveryRejectsClientConfirmationWhenOriginalsWereNotSaved() {
        Booking booking = deliveryBooking("""
                {
                  "startedAt": 100,
                  "checkpoints": [
                    { "id": "pickup", "type": "pickup", "arrivedAt": 100, "doneAt": 101 },
                    { "id": "delivery", "type": "delivery", "arrivedAt": 200, "photo": "arrival.jpg", "handoverPhoto": "signed.jpg" }
                  ],
                  "welfare": [{ "checkpointId": "delivery" }]
                }
                """);
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));

        var request = new com.example.racehorse_transport.dto.field.TripUpdateRequest();
        request.setTripId("TRP-042-1");
        request.setAction("COMPLETE_DELIVERY");
        request.setActor("driver02");
        request.setPayload(Map.of("returnedOriginals", true));

        assertThatThrownBy(() -> service.updateTripProgress("BK-042", request, "manager", "MANAGER"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("original documents were returned");

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void driverCannotStartTripAssignedToAnotherDriver() {
        Booking booking = fieldTripBooking(42, "ready_for_pickup", "\"driverId\":\"DR-02\"");
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        when(staffRepository.findByUser_Username("driver01")).thenReturn(Optional.of(fieldDriver()));

        var request = new com.example.racehorse_transport.dto.field.TripUpdateRequest();
        request.setTripId("TRP-042-1");
        request.setAction("DEPART_TO_PICKUP");

        assertThatThrownBy(() -> service.updateTripProgress("BK-042", request, "driver01", "DRIVER"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("không được phân công");
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void driverCannotCheckInAnotherTripWhileAssignedTripIsStillActive() {
        Booking target = fieldTripBooking(42, "ready_for_pickup", "\"driverId\":\"DR-01\"");
        Booking active = fieldTripBooking(43, "in_transit", "\"driverId\":\"DR-01\",\"departedAt\":100");
        when(bookingRepository.findById(42)).thenReturn(Optional.of(target));
        when(bookingRepository.findAll()).thenReturn(List.of(target, active));
        when(staffRepository.findByUser_Username("driver01")).thenReturn(Optional.of(fieldDriver()));

        var request = new com.example.racehorse_transport.dto.field.TripUpdateRequest();
        request.setTripId("TRP-042-1");
        request.setAction("ARRIVE_PICKUP");

        assertThatThrownBy(() -> service.updateTripProgress("BK-042", request, "driver01", "DRIVER"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BK-043")
                .hasMessageContaining("chưa xác nhận về trụ sở");
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    private Booking fieldTripBooking(Integer id, String status, String tripFields) {
        String tripId = String.format("TRP-%03d-1", id);
        return Booking.builder()
                .id(id)
                .status(status)
                .detailsJson("{\"trips\":[{\"tripId\":\"" + tripId + "\"," + tripFields + "}]}")
                .build();
    }

    private Staff fieldDriver() {
        User user = new User();
        user.setUsername("driver01");
        Staff staff = new Staff();
        staff.setId(1);
        staff.setUser(user);
        staff.setRole("DRIVER");
        staff.setStaffCode("DR-01");
        return staff;
    }

    @Test
    void completeDeliverySavesDestinationAndCanBookHorseAgainAfterSettlement() throws Exception {
        Booking booking = deliveredHorseBooking(false);
        Horse horse = Horse.builder().id(12).customerID(customer).transitStatus("IN_TRANSIT").build();
        when(horseRepository.findById(12)).thenReturn(Optional.of(horse));
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(43);
            return saved;
        });
        var delivery = new com.example.racehorse_transport.dto.field.TripUpdateRequest();
        delivery.setTripId("TRP-042-1");
        delivery.setAction("COMPLETE_DELIVERY");
        service.updateTripProgress("BK-042", delivery, "manager", "MANAGER");

        assertThat(horse.getCurrentCountry()).isEqualTo("VN");
        assertThat(horse.getCurrentLocationName()).isEqualTo("Trang trại A, Hà Nội");
        assertThat(horse.getLocationSource()).isEqualTo("SYSTEM_DELIVERY");
        assertThat(booking.getStatus()).isEqualTo("delivered_pending_settlement");
        verify(horseJourneyLogRepository).save(any());

        // Settlement closes the old order; the saved location must allow another pickup in VN.
        booking.setStatus("completed");
        when(userRepository.findByUsernameOrEmail("phuc", "phuc")).thenReturn(Optional.of(customer.getUser()));
        when(customerRepository.findById(7)).thenReturn(Optional.of(customer));
        when(bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(7)).thenReturn(List.of(booking));
        var next = CreateBookingRequest.builder().type("domestic")
                .originName("Trang trại A, Hà Nội").originCountry("VN")
                .destName("Trang trại B, Đà Nẵng").destCountry("VN")
                .departAt(Instant.now().plus(25, java.time.temporal.ChronoUnit.DAYS).toEpochMilli())
                .consignor(PartyContactDto.builder().name("Sender").phone("0900000000").build())
                .consignee(PartyContactDto.builder().name("Recipient").phone("0900000001").build())
                .horses(List.of(BookingHorseDto.builder().horseId("H-012").name("Lightning")
                        .insurance(com.example.racehorse_transport.dto.booking.HorseInsuranceDto.builder()
                                .option("none").build()).build())).build();
        assertThat(service.createBooking("phuc", next).getStatus()).isEqualTo("pending_intake");
    }

    @Test
    void firstVehicleDeliveryDoesNotReleaseOtherHorsesOrStartSettlement() throws Exception {
        Booking booking = deliveredHorseBooking(true);
        Horse horse = Horse.builder().id(12).transitStatus("IN_TRANSIT").build();
        when(horseRepository.findById(12)).thenReturn(Optional.of(horse));
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var delivery = new com.example.racehorse_transport.dto.field.TripUpdateRequest();
        delivery.setTripId("TRP-042-1");
        delivery.setAction("COMPLETE_DELIVERY");
        service.updateTripProgress("BK-042", delivery, "manager", "MANAGER");
        verify(horseRepository).save(horse);
        verify(horseRepository, never()).findById(13);
        assertThat(booking.getStatus()).isEqualTo("in_transit");
    }

    private Booking deliveredHorseBooking(boolean multipleVehicles) throws Exception {
        Booking booking = deliveryBooking("""
                {"startedAt":100,"checkpoints":[
                  {"id":"pickup","type":"pickup","doneAt":101},
                  {"id":"delivery","type":"delivery","arrivedAt":200,"photo":"arrival.jpg",
                   "handoverPhoto":"signed.jpg","returnedOriginals":true}],
                 "welfare":[{"checkpointId":"delivery"}]}
                """);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var payload = mapper.readValue(booking.getDetailsJson(),
                com.example.racehorse_transport.dto.booking.BookingDetailsPayload.class);
        payload.setDestCountry("VN");
        payload.getTrips().getFirst().setHorseIds(List.of("H-012"));
        var horses = new java.util.ArrayList<BookingHorseDto>();
        horses.add(BookingHorseDto.builder().horseId("HORSE-12").build());
        if (multipleVehicles) {
            horses.add(BookingHorseDto.builder().horseId("H-013").build());
            var trips = new java.util.ArrayList<>(payload.getTrips());
            var second = new com.example.racehorse_transport.dto.coordinator.VehicleTripDto();
            second.setTripId("TRP-042-2");
            second.setHorseIds(List.of("H-013"));
            second.setRun(new java.util.HashMap<>(Map.of("startedAt", 100)));
            trips.add(second);
            payload.setTrips(trips);
        }
        payload.setHorses(horses);
        booking.setDropoffAddress("Trang trại A, Hà Nội");
        booking.setDetailsJson(mapper.writeValueAsString(payload));
        return booking;
    }

    private Booking deliveryBooking(String runJson) {
        return Booking.builder()
                .id(42)
                .status("in_transit")
                .bookingType("domestic")
                .pickupAddress("Điểm đón")
                .dropoffAddress("Điểm giao")
                .detailsJson("""
                        {
                          "trips": [{
                            "tripId": "TRP-042-1",
                            "horseIds": [],
                            "run": %s
                          }]
                        }
                        """.formatted(runJson))
                .build();
    }

    @Test
    void getBookingById_rejectsAnotherCustomer() {
        when(bookingRepository.findById(42)).thenReturn(Optional.of(bookingOwnedByCustomer()));

        assertThatThrownBy(() -> service.getBookingById(42, "another-customer"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("không có quyền xem đơn hàng này");
    }

    @Test
    @SuppressWarnings("unchecked")
    void fieldCrewStillSeesBookingsWaitingForHandoverAfterArrival() {
        when(staffRepository.findByUser_Username("driver02")).thenReturn(Optional.empty());
        when(staffRepository.findAll()).thenReturn(List.of());
        ArgumentCaptor<java.util.Collection<String>> statuses = ArgumentCaptor.forClass(java.util.Collection.class);
        when(bookingRepository.findByStatusInOrderByBookingDateDesc(statuses.capture())).thenReturn(List.of());

        service.getMyFieldBookings("driver02", "DRIVER");

        assertThat(statuses.getValue()).contains("arrived", "handover_timeout", "returning_to_hq");
    }

    @Test
    void fieldCrewKeepsTripAfterSettlementStartsUntilBackAtHq() {
        Staff driver = staff("driver02", "0900000002");
        driver.setId(5);
        driver.setStaffCode("DR-002");
        when(staffRepository.findByUser_Username("driver02")).thenReturn(Optional.of(driver));
        Booking returning = bookingOwnedByCustomer();
        returning.setStatus("expenses_submitted");
        returning.setDetailsJson("{\"trips\":[{\"tripId\":\"TRP-042-1\",\"driverId\":\"DR-002\","
                + "\"homeReturn\":{\"fromPlace\":\"Tokyo\",\"eta\":1}}]}");
        Booking backHome = bookingOwnedByCustomer();
        backHome.setId(43);
        backHome.setStatus("completed");
        backHome.setDetailsJson("{\"trips\":[{\"tripId\":\"TRP-043-1\",\"driverId\":\"DR-002\","
                + "\"homeReturn\":{\"fromPlace\":\"Tokyo\",\"eta\":1,\"backAt\":2}}]}");
        when(bookingRepository.findByStatusInOrderByBookingDateDesc(any())).thenAnswer(invocation -> {
            java.util.Collection<String> statuses = invocation.getArgument(0);
            return java.util.stream.Stream.of(returning, backHome).filter(b -> statuses.contains(b.getStatus())).toList();
        });

        List<BookingResponse> responses = service.getMyFieldBookings("driver02", "DRIVER");

        assertThat(responses).extracting(BookingResponse::getNumericId).containsExactly(42);
    }

    @Test
    void confirmCoordinatorPlan_rejectsRouteWithoutLegs() {
        Booking underReview = bookingOwnedByCustomer();
        underReview.setStatus("under_review"); // điều phối chỉ chốt phương án ở bước thẩm định
        when(bookingRepository.findById(42)).thenReturn(Optional.of(underReview));
        var trip = new com.example.racehorse_transport.dto.coordinator.VehicleTripDto();
        trip.setVehicleId("VH-001");
        trip.setHorseIds(List.of("H-001"));

        for (Object route : List.of("Hà Nội → Lạng Sơn", Map.of("rests", List.of()))) {
            var request = com.example.racehorse_transport.dto.coordinator.CoordinatorPlanRequest.builder()
                    .trips(List.of(trip)).route(route).build();
            assertThatThrownBy(() -> service.confirmCoordinatorPlan("BK-042", request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("chặng");
        }
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void confirmCoordinatorPlan_countsExtendedStallAsTwoCompartments() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("under_review");
        booking.setDetailsJson("""
                {"horses":[
                  {"horseId":"H-001","stall":"single","selectedServices":{"stall":{"id":2,"serviceCode":"STALL_SINGLE_EXTENDED"}}},
                  {"horseId":"H-002","stall":"single","selectedServices":{"stall":{"id":2,"serviceCode":"STALL_SINGLE_EXTENDED"}}}
                ]}
                """);
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        Vehicle vehicle = new Vehicle();
        vehicle.setId(1);
        vehicle.setCapacity(2);
        vehicle.setVehicleCode("VH-001");
        vehicle.setLicensePlate("29H-12345");
        when(fleetService.findVehicleByIdOrCode("VH-001")).thenReturn(vehicle);

        var trip = new com.example.racehorse_transport.dto.coordinator.VehicleTripDto();
        trip.setVehicleId("VH-001");
        trip.setHorseIds(List.of("H-001", "H-002"));
        var request = com.example.racehorse_transport.dto.coordinator.CoordinatorPlanRequest.builder()
                .trips(List.of(trip))
                .route(Map.of("legs", List.of(Map.of("from", "A", "to", "B"))))
                .build();

        assertThatThrownBy(() -> service.confirmCoordinatorPlan("BK-042", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("29H-12345")
                .hasMessageContaining("4 ngăn");
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void confirmCoordinatorPlan_setsRouteIdWhenProvided() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("under_review"); // điều phối chỉ chốt phương án ở bước thẩm định
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Vehicle vehicle = new Vehicle();
        vehicle.setId(1);
        vehicle.setCapacity(4);
        vehicle.setVehicleCode("VH-001");
        when(fleetService.findVehicleByIdOrCode("VH-001")).thenReturn(vehicle);

        Route route = new Route();
        route.setId(5);
        route.setRouteName("Tuyến test");
        when(routeRepository.findById(5)).thenReturn(Optional.of(route));

        var trip = new com.example.racehorse_transport.dto.coordinator.VehicleTripDto();
        trip.setVehicleId("VH-001");
        trip.setHorseIds(List.of("H-001"));

        var planRequest = com.example.racehorse_transport.dto.coordinator.CoordinatorPlanRequest.builder()
                .routeId(5)
                .trips(List.of(trip))
                .route(Map.of("legs", List.of(Map.of("from", "A", "to", "B"))))
                .build();

        BookingResponse res = service.confirmCoordinatorPlan("BK-042", planRequest);

        assertThat(res.getRouteId()).isEqualTo(5);
        assertThat(booking.getRouteID()).isEqualTo(route);
        verify(routeRepository).findById(5);
    }

    @Test
    void readingBookingPreservesApprovedSosCheckpointsInsteadOfRebuildingOriginalRoute() {
        Booking booking = bookingOwnedByCustomer(); booking.setStatus("emergency_plan_active");
        booking.setDetailsJson("""
                {"route":{"legs":[{"arriveAt":100}],"rests":[{"name":"Mốc cũ đã thay"}]},
                 "trips":[{"tripId":"T1","incidentRoute":{"incidentId":7,"action":"to_station","newEta":300},
                   "run":{"startedAt":1,"checkpoints":[{"id":"pickup","type":"pickup","doneAt":2},
                     {"id":"sos-7","type":"rest","incidentId":7,"place":"Trạm SOS","plannedAt":200},
                     {"id":"delivery","type":"delivery","plannedAt":300}]}}]}
                """);
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        BookingResponse response = service.getBookingById(42, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) response.getTrips().get(0).getRun().get("checkpoints");
        assertThat(checkpoints).extracting(cp -> cp.get("id")).containsExactly("pickup", "sos-7", "delivery");
        assertThat(checkpoints.get(2).get("plannedAt")).isEqualTo(300);
    }

    @Test
    void getBookingById_rejectsUnknownBooking() {
        when(bookingRepository.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBookingById(404, "phuc"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy đơn hàng có ID: 404");
    }

    @Test
    void getCustomerBookings_returnsBookingsForAuthenticatedCustomer() {
        Booking booking = bookingOwnedByCustomer();
        booking.setBookingType("international");
        booking.setPickupAddress("Hanoi");
        booking.setDropoffAddress("Tokyo");
        when(userRepository.findByUsernameOrEmail("phuc", "phuc")).thenReturn(Optional.of(customer.getUser()));
        when(bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(7)).thenReturn(List.of(booking));

        List<BookingResponse> responses = service.getCustomerBookings("phuc");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getNumericId()).isEqualTo(42);
        assertThat(responses.get(0).getCustomer()).isEqualTo("phuc");
        assertThat(responses.get(0).getStatus()).isEqualTo("pending_intake");
        assertThat(responses.get(0).getType()).isEqualTo("international");
        assertThat(responses.get(0).getOriginName()).isEqualTo("Hanoi");
        assertThat(responses.get(0).getDestName()).isEqualTo("Tokyo");
        verify(bookingRepository).findByCustomerID_IdOrderByBookingDateDesc(7);
    }

    @Test
    void getCustomerBookings_rejectsUnknownUser() {
        when(userRepository.findByUsernameOrEmail("missing", "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCustomerBookings("missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy thông tin người dùng: missing");

        verify(bookingRepository, never()).findByCustomerID_IdOrderByBookingDateDesc(any(Integer.class));
    }

    @Test
    void getCustomerTripTeam_returnsVehicleAndCrewOfEachTrip() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("waybill_issued");
        booking.setDetailsJson("{\"horses\":[{\"horseId\":\"H-001\",\"name\":\"Storm\"}],"
                + "\"trips\":[{\"tripId\":\"TRP-042-1\",\"vehicleId\":\"VH-001\",\"driverId\":\"DR-01\",\"escortId\":\"ES-01\",\"horseIds\":[\"H-001\"]}]}");
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        Vehicle vehicle = Vehicle.builder().id(1).vehicleType("Xe 2 ngăn").licensePlate("51C-123.45").build();
        when(fleetService.findVehicleByIdOrCode("VH-001")).thenReturn(vehicle);
        when(fleetService.findStaffByIdOrCode("DR-01", "DRIVER")).thenReturn(staff("driver01", "0901000001"));
        when(fleetService.findStaffByIdOrCode("ES-01", "ESCORT")).thenReturn(staff("escort01", "0901000002"));

        List<CustomerTripTeamResponse> team = service.getCustomerTripTeam(42, "phuc");

        assertThat(team).hasSize(1);
        CustomerTripTeamResponse trip = team.get(0);
        assertThat(trip.getTripId()).isEqualTo("TRP-042-1");
        assertThat(trip.getHorseNames()).containsExactly("Storm");
        assertThat(trip.getVehicle().getPlate()).isEqualTo("51C-123.45");
        assertThat(trip.getVehicle().getStalls()).isEqualTo(2);
        assertThat(trip.getDriver().getName()).isEqualTo("driver01");
        assertThat(trip.getEscort().getPhone()).isEqualTo("0901000002");
    }

    @Test
    void getCustomerTripTeam_isEmptyBeforeTheQuoteAndForOtherCustomers() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("pending_commercial");
        booking.setDetailsJson("{\"trips\":[{\"tripId\":\"TRP-042-1\",\"vehicleId\":\"VH-001\",\"driverId\":\"DR-01\",\"escortId\":\"ES-01\",\"horseIds\":[]}]}");
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));

        assertThat(service.getCustomerTripTeam(42, "phuc")).isEmpty();
        assertThatThrownBy(() -> service.getCustomerTripTeam(42, "another-customer")).isInstanceOf(AccessDeniedException.class);
        verify(fleetService, never()).findVehicleByIdOrCode(any());
    }

    @Test
    void getBookingsByStatus_filtersAndMapsBookings() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("under_review");
        when(bookingRepository.findByStatusOrderByBookingDateDesc("under_review")).thenReturn(List.of(booking));

        List<BookingResponse> responses = service.getBookingsByStatus("under_review");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getNumericId()).isEqualTo(42);
        assertThat(responses.get(0).getStatus()).isEqualTo("under_review");
        verify(bookingRepository).findByStatusOrderByBookingDateDesc("under_review");
        verify(bookingRepository, never()).findAllByOrderByBookingDateDesc();
    }

    @Test
    void getBookingsByStatus_returnsAllForNullBlankOrAllFilter() {
        Booking booking = bookingOwnedByCustomer();
        when(bookingRepository.findAllByOrderByBookingDateDesc()).thenReturn(List.of(booking));

        for (String status : List.of("", "  ", "ALL")) {
            List<BookingResponse> responses = service.getBookingsByStatus(status);
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getNumericId()).isEqualTo(42);
        }
        assertThat(service.getBookingsByStatus(null)).hasSize(1);

        verify(bookingRepository, org.mockito.Mockito.times(4)).findAllByOrderByBookingDateDesc();
        verify(bookingRepository, never()).findByStatusOrderByBookingDateDesc(any(String.class));
    }

    @Test
    void resubmitBooking_resetsMedicalReviewForOwner() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("resubmission_requested");
        booking.setDetailsJson("""
                {
                  "medical": {
                    "status": "rejected",
                    "reason": "Thiếu chứng từ",
                    "items": ["health certificate"],
                    "requestedAt": 1000,
                    "requestedBy": "specialist"
                  },
                  "history": []
                }
                """);
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = service.resubmitBooking("BK-042", "phuc");

        assertThat(response.getStatus()).isEqualTo("under_review");
        assertThat(response.getMedical()).isInstanceOf(Map.class);
        Map<?, ?> medical = (Map<?, ?>) response.getMedical();
        assertEquals("pending", medical.get("status"));
        assertTrue(medical.containsKey("resubmittedAt"));
        assertFalse(medical.containsKey("reason"));
        assertFalse(medical.containsKey("items"));
        assertFalse(medical.containsKey("requestedAt"));
        assertFalse(medical.containsKey("requestedBy"));
        assertThat(response.getHistory()).hasSize(1);
        assertThat(response.getHistory().get(0))
                .containsEntry("actor", "phuc")
                .containsEntry("text", "Bổ sung hồ sơ và gửi lại thẩm định");
        verify(bookingRepository).save(booking);
    }

    @Test
    void resubmitBooking_rejectsAnotherCustomer() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("resubmission_requested");
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.resubmitBooking("42", "another-customer"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("không có quyền");

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void resubmitBooking_rejectsBookingInAnotherStatus() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("pending_intake");
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.resubmitBooking("ORD-42", "phuc"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("không chờ khách bổ sung hồ sơ");

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void rejectQuote_cancelsBookingAndReleasesReservedResources() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("awaiting_payment");
        booking.setDetailsJson("{\"history\":[]}");
        Quotation quotation = Quotation.builder().booking(booking).status("PENDING").build();
        when(bookingRepository.findByIdForUpdate(42)).thenReturn(Optional.of(booking));
        when(quotationRepository.findTopByBookingIdOrderBySentAtDesc(42)).thenReturn(Optional.of(quotation));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = service.rejectQuote(
                "BK-042", "phuc", RejectQuoteRequest.builder().reason("  Giá vượt ngân sách  ").build());

        assertThat(response.getStatus()).isEqualTo("cancelled");
        assertThat(quotation.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getHistory()).hasSize(1);
        assertThat(response.getHistory().get(0))
                .containsEntry("actor", "phuc")
                .containsEntry("text", "Từ chối báo giá");
        verify(quotationRepository).save(quotation);
        verify(bookingVehicleRepository).deleteByBookingID_Id(42);
        verify(bookingAssignmentRepository).deleteByBookingID_Id(42);
        verify(bookingRepository).save(booking);
        verify(systemLogRepository).save(any());
    }

    @Test
    void rejectQuote_usesDefaultReasonAndAllowsMissingQuotation() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("awaiting_payment");
        when(bookingRepository.findByIdForUpdate(42)).thenReturn(Optional.of(booking));
        when(quotationRepository.findTopByBookingIdOrderBySentAtDesc(42)).thenReturn(Optional.empty());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = service.rejectQuote("42", "phuc", null);

        assertThat(response.getStatus()).isEqualTo("cancelled");
        assertThat(response.getHistory().get(0).get("text"))
                .isEqualTo("Từ chối báo giá");
        verify(quotationRepository, never()).save(any(Quotation.class));
        verify(bookingVehicleRepository).deleteByBookingID_Id(42);
        verify(bookingAssignmentRepository).deleteByBookingID_Id(42);
    }

    @Test
    void rejectQuote_rejectsBookingNotWaitingForDeposit() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("under_review");
        when(bookingRepository.findByIdForUpdate(42)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.rejectQuote("42", "phuc", RejectQuoteRequest.builder().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chờ đặt cọc");

        verify(quotationRepository, never()).findTopByBookingIdOrderBySentAtDesc(42);
        verify(bookingVehicleRepository, never()).deleteByBookingID_Id(42);
        verify(bookingAssignmentRepository, never()).deleteByBookingID_Id(42);
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(systemLogRepository, never()).save(any());
    }

    @Test
    void insuranceDeadlineCapsQuoteAndExpiredInsuranceBlocksIssuing() {
        when(clock.instant()).thenReturn(Instant.now());
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("pending_commercial");
        String today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toString();
        String details = """
            {"horses":[{"horseId":"H1","insurance":{"option":"assistance","provider":"Provider","premium":1250000,"reviewed":true,"quoteValidUntil":"%s"}}],"medical":{"status":"approved"},"plan":{"at":1},"trips":[{"tripId":"T1","driverId":"D1","escortId":"E1"}]}
            """.formatted(today);
        booking.setDetailsJson(details);
        when(bookingRepository.findByIdForUpdate(42)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        var line = com.example.racehorse_transport.dto.manager.QuoteLineDto.builder().label("Cước").amount(new java.math.BigDecimal("1000000")).build();
        var request = com.example.racehorse_transport.dto.manager.SendQuoteRequest.builder().lines(List.of(line)).adjustments(List.of()).build();
        service.sendQuote("BK-42", request);
        ArgumentCaptor<Quotation> quote = ArgumentCaptor.forClass(Quotation.class);
        verify(quotationRepository).save(quote.capture());
        assertThat(quote.getValue().getExpiresAt()).isEqualTo(java.time.LocalDate.parse(today).plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().minusMillis(1));
        booking.setStatus("pending_commercial");
        booking.setDetailsJson(details.replace(today, "2020-01-01"));
        assertThatThrownBy(() -> service.sendQuote("BK-42", request)).hasMessageContaining("hết hạn");
    }

    @Test
    void lateIntakeAndQuoteAreBlockedEvenBeforeTheScheduleScannerRuns() {
        Booking booking = bookingOwnedByCustomer();
        booking.setDepartureDate(Instant.parse("2026-11-29T17:00:00Z"));
        when(clock.instant()).thenReturn(Instant.parse("2026-11-30T17:00:00Z"));
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByIdForUpdate(42)).thenReturn(Optional.of(booking));
        booking.setStatus("pending_intake");
        assertThatThrownBy(() -> service.activateBooking("BK-42", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("xác nhận lại lịch");
        booking.setStatus("pending_commercial");
        assertThatThrownBy(() -> service.sendQuote("BK-42", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("xác nhận lại lịch");
        verify(quotationRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void sendQuoteUsesSpecialistPremiumAndSnapshotsInsuranceInsteadOfClientAmount() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("pending_commercial");
        booking.setDetailsJson("""
            {"horses":[{"horseId":"H1","name":"Storm","insurance":{"option":"assistance","provider":"Provider","premium":1250000,"reviewed":true,"documents":["https://example.com/quote.pdf"]}}],
             "medical":{"status":"approved"},"plan":{"at":1},"trips":[{"tripId":"T1","driverId":"D1","escortId":"E1","horseIds":["H1"]}]}
            """);
        when(bookingRepository.findByIdForUpdate(42)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var transport = com.example.racehorse_transport.dto.manager.QuoteLineDto.builder().label("Cước vận chuyển").amount(new java.math.BigDecimal("10000000")).build();
        var tampered = com.example.racehorse_transport.dto.manager.QuoteLineDto.builder().label("Thu hộ bảo hiểm bên thứ ba").amount(java.math.BigDecimal.ONE).build();
        var request = com.example.racehorse_transport.dto.manager.SendQuoteRequest.builder().by("manager01").lines(List.of(transport, tampered)).adjustments(List.of()).build();
        service.sendQuote("BK-42", request);
        ArgumentCaptor<Quotation> saved = ArgumentCaptor.forClass(Quotation.class);
        verify(quotationRepository).save(saved.capture());
        assertThat(saved.getValue().getTotalAmount()).isEqualByComparingTo("11250000");
        assertThat(saved.getValue().getQuoteLinesJson()).contains("insuranceSnapshot", "quote.pdf");
        assertThat(booking.getDetailsJson()).contains("insuranceSnapshot");
    }

    @Test
    void sendQuote_computesTheTotalsItselfAndIgnoresTheOnesSent() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("pending_commercial");
        booking.setDetailsJson("{\"horses\":[{\"horseId\":\"H1\",\"insurance\":{\"option\":\"none\"}}],\"medical\":{\"status\":\"approved\"},\"plan\":{\"at\":1},"
                + "\"trips\":[{\"tripId\":\"TRP-042-1\",\"vehicleId\":\"VH-001\",\"driverId\":\"DR-01\",\"escortId\":\"ES-01\",\"horseIds\":[]}]}");
        when(bookingRepository.findByIdForUpdate(42)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var line = new com.example.racehorse_transport.dto.manager.QuoteLineDto();
        line.setLabel("Cước vận chuyển");
        line.setAmount(new java.math.BigDecimal("10000000"));
        var request = com.example.racehorse_transport.dto.manager.SendQuoteRequest.builder()
                .by("manager01").lines(List.of(line)).adjustments(List.of())
                .subtotal(java.math.BigDecimal.ONE).total(java.math.BigDecimal.ONE)
                .deposit(java.math.BigDecimal.ONE).balance(java.math.BigDecimal.ZERO).build();

        service.sendQuote("BK-042", request);

        ArgumentCaptor<Quotation> saved = ArgumentCaptor.forClass(Quotation.class);
        verify(quotationRepository).save(saved.capture());
        assertThat(saved.getValue().getSubtotal()).isEqualByComparingTo("10000000");
        assertThat(saved.getValue().getTotalAmount()).isEqualByComparingTo("10000000");
        assertThat(saved.getValue().getDepositAmount()).isEqualByComparingTo("3000000");
        assertThat(saved.getValue().getBalanceAmount()).isEqualByComparingTo("7000000");
        assertThat(booking.getDetailsJson()).contains("\"total\":10000000").contains("\"deposit\":3000000");
        // Khách nhận mail báo giá với đúng số tiền hệ thống tự tính
        verify(billMailService).quoteIssued(org.mockito.ArgumentMatchers.eq(booking), org.mockito.ArgumentMatchers.eq(List.of(line)),
                org.mockito.ArgumentMatchers.argThat(v -> v.compareTo(new java.math.BigDecimal("10000000")) == 0),
                org.mockito.ArgumentMatchers.argThat(v -> v.compareTo(new java.math.BigDecimal("3000000")) == 0),
                org.mockito.ArgumentMatchers.argThat(v -> v.compareTo(new java.math.BigDecimal("7000000")) == 0),
                any());

        ArgumentCaptor<Notification> notice = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notice.capture());
        assertThat(notice.getValue().getRecipientRole()).isEqualTo("customer");
        assertThat(notice.getValue().getRecipientUsername()).isEqualTo("phuc");
        assertThat(notice.getValue().getTitle()).contains("BK-42");
        assertThat(notice.getValue().getBookingId()).isEqualTo("BK-42");
        assertThat(notice.getValue().getLink()).isEqualTo("/orders/BK-42");
        assertThat(notice.getValue().getIsRead()).isFalse();
    }

    @Test
    void rejectBooking_recordsManagerRejection() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("pending_intake");
        booking.setDetailsJson("{\"history\":[]}");
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = service.rejectBooking(
                "BK-042",
                RejectBookingRequest.builder()
                        .by("manager1")
                        .role("manager")
                        .reason("  Hồ sơ không đầy đủ  ")
                        .build());

        assertThat(response.getStatus()).isEqualTo("rejected");
        assertThat(response.getRejection())
                .isInstanceOfSatisfying(Map.class, rejection -> {
                    assertThat(rejection.get("by")).isEqualTo("manager1");
                    assertThat(rejection.get("role")).isEqualTo("manager");
                    assertThat(rejection.get("reason")).isEqualTo("Hồ sơ không đầy đủ");
                });
        assertThat(response.getHistory()).hasSize(1);
        assertThat(response.getHistory().get(0))
                .containsEntry("actor", "manager1")
                .containsEntry("text", "Từ chối đơn");
        verify(bookingRepository).save(booking);
        verify(systemLogRepository).save(any());
    }

    @Test
    void rejectBooking_allowsCoordinatorOnlyDuringReview() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("under_review");
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = service.rejectBooking(
                "ORD-42",
                RejectBookingRequest.builder()
                        .by("coordinator1")
                        .role("coordinator")
                        .reason("Không thể sắp xếp lộ trình")
                        .build());

        assertThat(response.getStatus()).isEqualTo("rejected");
        assertThat(response.getRejection())
                .isInstanceOfSatisfying(Map.class, rejection -> {
                    assertThat(rejection.get("role")).isEqualTo("coordinator");
                    assertThat(rejection.get("reason")).isEqualTo("Không thể sắp xếp lộ trình");
                });
        verify(bookingRepository).save(booking);
    }

    @Test
    void rejectBooking_rejectsManagerWhenBookingIsNotPendingIntake() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("under_review");
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.rejectBooking(
                "42",
                RejectBookingRequest.builder().role("manager").reason("reason").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending_intake");

        verify(bookingRepository, never()).save(any(Booking.class));
        verify(systemLogRepository, never()).save(any());
    }

    @Test
    void rejectBooking_rejectsCoordinatorWhenBookingIsNotUnderReview() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("pending_intake");
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.rejectBooking(
                "42",
                RejectBookingRequest.builder().role("coordinator").reason("reason").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("under_review");

        verify(bookingRepository, never()).save(any(Booking.class));
        verify(systemLogRepository, never()).save(any());
    }

    private Staff staff(String username, String phone) {
        User user = new User();
        user.setUsername(username);
        Staff staff = new Staff();
        staff.setUser(user);
        staff.setPhone(phone);
        return staff;
    }

    private Booking bookingOwnedByCustomer() {
        return Booking.builder()
                .id(42)
                .customerID(customer)
                .status("pending_intake")
                .bookingDate(Instant.parse("2026-10-20T10:00:00Z"))
                .departureDate(Instant.parse("2027-01-15T10:00:00Z"))
                .build();
    }

    private void givenCustomerCanCreateBooking() {
        when(userRepository.findByUsernameOrEmail("phuc", "phuc")).thenReturn(Optional.of(customer.getUser()));
        when(customerRepository.findById(7)).thenReturn(Optional.of(customer));
        when(bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(7)).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking saved = invocation.getArgument(0);
            saved.setId(42);
            return saved;
        });
    }

    private CreateBookingRequest requestWithHorseId(String horseId) {
        return CreateBookingRequest.builder()
                .type("domestic")
                .originName("Hanoi")
                .destName("Ho Chi Minh City")
                .departAt(Instant.parse("2027-01-15T10:00:00Z").toEpochMilli())
                .consignor(PartyContactDto.builder().name("Sender").phone("0900").build())
                .consignee(PartyContactDto.builder().name("Receiver").phone("0901").build())
                .horses(List.of(BookingHorseDto.builder().insurance(com.example.racehorse_transport.dto.booking.HorseInsuranceDto.builder().option("none").build()).horseId(horseId).build()))
                .build();
    }

    @Test
    void customerCannotReadDraftThirdPartyPremiumOrDocumentsBeforeQuotation() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("pending_commercial");
        booking.setDetailsJson("""
            {"horses":[{"horseId":"H1","insurance":{"option":"assistance","provider":"Internal provider","premium":1250000,"reviewed":true,"documents":["https://example.com/draft.pdf"]}}]}
            """);
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        context.setAuthentication(new org.springframework.security.authentication.TestingAuthenticationToken("phuc", null, "ROLE_CUSTOMER"));
        try {
            var insurance = service.getBookingById(42, "phuc").getHorses().getFirst().getInsurance();
            assertThat(insurance.getOption()).isEqualTo("assistance");
            assertThat(insurance.getPremium()).isNull();
            assertThat(insurance.getProvider()).isNull();
            assertThat(insurance.getDocuments()).isNull();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void customerSeesFrozenQuoteButNotDraftPolicy() {
        Booking order = bookingOwnedByCustomer();
        order.setStatus("clearance_in_progress");
        order.setDetailsJson("""
          {"horses":[{"horseId":"H1","insurance":{"option":"assistance","provider":"Changed","premium":999,"policyNumber":"DRAFT","policyDocuments":["https://example.test/draft.pdf"],"arranged":false}}],"quote":{"insuranceSnapshot":[{"horseId":"H1","insurance":{"option":"assistance","provider":"Quoted","premium":1250000,"documents":["https://example.test/quote.pdf"],"reviewed":true}}]}}
          """);
        when(bookingRepository.findById(42)).thenReturn(Optional.of(order));
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        context.setAuthentication(new org.springframework.security.authentication.TestingAuthenticationToken("phuc", null, "ROLE_CUSTOMER"));
        try {
            var insurance = service.getBookingById(42, "phuc").getHorses().getFirst().getInsurance();
            assertThat(insurance.getProvider()).isEqualTo("Quoted");
            assertThat(insurance.getPremium()).isEqualByComparingTo("1250000");
            assertThat(insurance.getDocuments()).containsExactly("https://example.test/quote.pdf");
            assertThat(insurance.getPolicyNumber()).isNull();
            assertThat(insurance.getPolicyDocuments()).isNull();
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    @Test
    void getBookingById_tellsTheCustomerWhatTheyCanDo() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("ready_for_pickup");
        booking.setDetailsJson("{\"payment\":{\"amount\":3000000}}");
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));

        BookingResponse beforeBalance = service.getBookingById(42, "phuc");
        assertThat(beforeBalance.getCanCancel()).isTrue();
        // 70% còn lại chỉ trả được sau khi kíp xe nhận xong ngựa ở điểm đón
        assertThat(beforeBalance.getCanPayBalance()).isFalse();
        assertThat(beforeBalance.getCancelPenalty()).isEqualByComparingTo("3000000");

        booking.setStatus("en_route_to_pickup");
        booking.setDetailsJson("{\"payment\":{\"amount\":3000000},"
                + "\"horses\":[{\"horseId\":\"H-1\",\"microchip\":\"VN-1\",\"status\":\"ELIGIBLE\"}],"
                + "\"trips\":[{\"tripId\":\"TRP-1\",\"horseIds\":[\"H-1\"],\"departedAt\":1,\"run\":{\"checkpoints\":[{\"id\":\"pickup\","
                + "\"type\":\"pickup\",\"arrivedAt\":1,\"photo\":\"p.jpg\",\"chips\":[\"VN-1\"],\"handoverPhoto\":\"h.jpg\","
                + "\"originals\":[\"Hộ chiếu ngựa bản gốc (FEI / National Passport)\",\"Sổ tiêm phòng\","
                + "\"Phiếu xét nghiệm EIA/EVA, bản gốc kèm 02 bản sao công chứng\"]}]}}]}");
        assertThat(service.getBookingById(42, "phuc").getCanPayBalance()).isTrue();

        booking.setDetailsJson("{\"payment\":{\"amount\":3000000,\"balancePaid\":true}}");
        BookingResponse afterBalance = service.getBookingById(42, "phuc");
        assertThat(afterBalance.getCanCancel()).isFalse();
        assertThat(afterBalance.getCanPayBalance()).isFalse();

        booking.setStatus("clearance_in_progress");
        booking.setDetailsJson("{\"payment\":{\"amount\":3000000}}");
        BookingResponse duringClearance = service.getBookingById(42, "phuc");
        assertThat(duringClearance.getCanCancel()).isTrue();
        assertThat(duringClearance.getCanPayBalance()).isFalse();
    }

    @Test
    void getCustomerBookingsPaged_returnsPagedResponse() {
        User user = new User();
        user.setId(10);
        user.setUsername("phuc");
        when(userRepository.findByUsernameOrEmail("phuc", "phuc")).thenReturn(Optional.of(user));

        Booking booking = bookingOwnedByCustomer();
        org.springframework.data.domain.Page<Booking> page = new org.springframework.data.domain.PageImpl<>(
                List.of(booking), org.springframework.data.domain.PageRequest.of(0, 10), 1);
        when(bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(any(Integer.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);

        com.example.racehorse_transport.dto.common.PagedResponse<BookingResponse> result = service.getCustomerBookingsPaged("phuc", 0, 10);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getPage()).isEqualTo(0);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.isLast()).isTrue();
    }

    private Booking prepareDepositExpiry(String paymentStatus, Instant now) {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("awaiting_payment");
        when(clock.instant()).thenReturn(now);
        when(bookingRepository.findIdsByStatusOrderByBookingDateAsc("awaiting_payment")).thenReturn(List.of(42));
        when(bookingRepository.findByIdForUpdate(42)).thenReturn(Optional.of(booking));
        when(quotationRepository.findTopByBookingIdOrderBySentAtDesc(42)).thenReturn(Optional.of(Quotation.builder()
                .booking(booking).sentAt(DEPOSIT_DEADLINE.minusSeconds(3600)).expiresAt(DEPOSIT_DEADLINE)
                .status("PENDING").build()));
        if (paymentStatus != null) {
            when(paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(42)).thenReturn(List.of(Payment.builder()
                    .bookingID(booking).paymentType("DEPOSIT").paymentMethod("VNPAY").status(paymentStatus)
                    .paymentDate(DEPOSIT_DEADLINE.minusSeconds(60)).build()));
        }
        return booking;
    }

    @Test
    void expiry_pendingDepositDuringGrace_keepsVehicleAndCrew() {
        Booking booking = prepareDepositExpiry("PENDING", DEPOSIT_DEADLINE.plusSeconds(299));

        assertThat(service.releaseExpiredQuotations()).isZero();
        assertThat(booking.getStatus()).isEqualTo("awaiting_payment");
        verify(bookingVehicleRepository, never()).deleteByBookingID_Id(42);
        verify(bookingAssignmentRepository, never()).deleteByBookingID_Id(42);
    }

    @Test
    void expiry_pendingDepositAtGraceCutoff_releasesAssignmentsWithoutAssumingFailure() {
        Booking booking = prepareDepositExpiry("PENDING", DEPOSIT_DEADLINE.plusSeconds(300));

        assertThat(service.releaseExpiredQuotations()).isEqualTo(1);
        assertThat(booking.getStatus()).isEqualTo("quote_expired");
        verify(bookingVehicleRepository).deleteByBookingID_Id(42);
        verify(bookingAssignmentRepository).deleteByBookingID_Id(42);
        var payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        assertThat(payment.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(payment.getValue().getNote()).contains("Chờ đối soát");
    }

    @Test
    void expiry_failedDepositDoesNotExtendHold() {
        prepareDepositExpiry("FAILED", DEPOSIT_DEADLINE.plusSeconds(1));
        assertThat(service.releaseExpiredQuotations()).isEqualTo(1);
        verify(bookingVehicleRepository).deleteByBookingID_Id(42);
        verify(bookingAssignmentRepository).deleteByBookingID_Id(42);
    }

    @Test
    void expiry_withoutPaymentDoesNotExtendHold() {
        prepareDepositExpiry(null, DEPOSIT_DEADLINE);
        assertThat(service.releaseExpiredQuotations()).isEqualTo(1);
    }

    @Test
    void expiry_pendingSessionFromOlderQuotationDoesNotExtendHold() {
        prepareDepositExpiry("PENDING", DEPOSIT_DEADLINE.plusSeconds(1));
        when(paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(42)).thenReturn(List.of(Payment.builder()
                .paymentType("DEPOSIT").paymentMethod("VNPAY").status("PENDING")
                .paymentDate(DEPOSIT_DEADLINE.minusSeconds(7200)).build()));
        assertThat(service.releaseExpiredQuotations()).isEqualTo(1);
    }

    @Test
    void readingAwaitingDepositExposesConfirmationDeadlineWithoutExtendingQuote() {
        Booking booking = prepareDepositExpiry("PENDING", DEPOSIT_DEADLINE.plusSeconds(120));
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        BookingResponse response = service.getBookingById(42, "phuc");
        assertThat(response.getDepositConfirmationUntil()).isEqualTo(DEPOSIT_DEADLINE.plusSeconds(300).toEpochMilli());
    }

    @Test
    void assignCrew_duringPaymentConfirmationCannotChangeExistingCrew() {
        Booking booking = prepareDepositExpiry("PENDING", DEPOSIT_DEADLINE.plusSeconds(120));
        assertThatThrownBy(() -> service.assignCrew("42", new com.example.racehorse_transport.dto.manager.AssignCrewRequest()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("pending_commercial");
        assertThat(booking.getStatus()).isEqualTo("awaiting_payment");
        verify(bookingAssignmentRepository, never()).deleteByBookingID_Id(42);
    }

    @Test
    void expiry_rechecksStatusAfterLockAndSkipsBookingAlreadyPaid() {
        Booking booking = prepareDepositExpiry("PENDING", DEPOSIT_DEADLINE.plusSeconds(300));
        booking.setStatus("waybill_issued");

        assertThat(service.releaseExpiredQuotations()).isZero();
        verify(quotationRepository, never()).findTopByBookingIdOrderBySentAtDesc(42);
        verify(bookingAssignmentRepository, never()).deleteByBookingID_Id(42);
    }

    @Test
    void expiry_neverReleasesCompletedDepositEvenWithInconsistentBookingStatus() {
        prepareDepositExpiry("COMPLETED", DEPOSIT_DEADLINE.plusSeconds(300));
        assertThat(service.releaseExpiredQuotations()).isZero();
        verify(bookingVehicleRepository, never()).deleteByBookingID_Id(42);
    }

    @Test
    void getBookingsPaged_returnsAllPagedWhenStatusAll() {
        Booking booking = bookingOwnedByCustomer();
        org.springframework.data.domain.Page<Booking> page = new org.springframework.data.domain.PageImpl<>(
                List.of(booking), org.springframework.data.domain.PageRequest.of(0, 20), 1);
        when(bookingRepository.findAllByOrderByBookingDateDesc(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);

        com.example.racehorse_transport.dto.common.PagedResponse<BookingResponse> result = service.getBookingsPaged("ALL", 0, 20);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }
}
