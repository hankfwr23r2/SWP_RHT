package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.booking.CreateBookingRequest;
import com.example.racehorse_transport.dto.booking.CustomerTripTeamResponse;
import com.example.racehorse_transport.dto.booking.PartyContactDto;
import com.example.racehorse_transport.dto.booking.RejectQuoteRequest;
import com.example.racehorse_transport.dto.manager.RejectBookingRequest;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.dto.admin.SystemConfigResponse;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingAssignmentRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingVehicleRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.LogRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.repository.RegulationRepository;
import com.example.racehorse_transport.repository.StaffRepository;
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
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final FleetService fleetService = mock(FleetService.class);
    private final LogRepository logRepository = mock(LogRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final StaffRepository staffRepository = mock(StaffRepository.class);
    private final RegulationRepository regulationRepository = mock(RegulationRepository.class);
    private final LocationRepository locationRepository = mock(LocationRepository.class);
    private final TripDocumentRepository tripDocumentRepository = mock(TripDocumentRepository.class);
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);

    private BookingServiceImpl service;
    private Customer customer;

    @BeforeEach
    void setUp() {
        service = new BookingServiceImpl(
                bookingRepository,
                userRepository,
                customerRepository,
                horseRepository,
                systemLogRepository,
                bookingVehicleRepository,
                bookingAssignmentRepository,
                quotationRepository,
                paymentRepository,
                fleetService,
                logRepository,
                vehicleRepository,
                staffRepository,
                regulationRepository,
                locationRepository,
                systemConfigService,
                tripDocumentRepository);

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
        BookingHorseDto horseDto = BookingHorseDto.builder().horseId("HORSE-12").name("Lightning").build();
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
        Horse horse = Horse.builder().id(12).customerID(customer).build();

        when(userRepository.findByUsernameOrEmail("phuc", "phuc")).thenReturn(Optional.of(customer.getUser()));
        when(customerRepository.findById(7)).thenReturn(Optional.of(customer));
        when(bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(7)).thenReturn(List.of());
        when(horseRepository.findById(12)).thenReturn(Optional.of(horse));
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

        ArgumentCaptor<Booking> savedBooking = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(savedBooking.capture());
        assertThat(savedBooking.getValue().getConsignorName()).isEqualTo("Sender");
        assertThat(savedBooking.getValue().getConsignorPhone()).isEqualTo("0900");
        assertThat(savedBooking.getValue().getDetailsJson()).contains("\"originCountry\":\"VN\"");
        verify(systemLogRepository).save(any());
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
        BookingHorseDto horseDto = BookingHorseDto.builder().horseId("HORSE-12").name("Lightning").build();
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
        BookingHorseDto horseDto = BookingHorseDto.builder().horseId("HORSE-12").name("Lightning").build();
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

        assertThatThrownBy(() -> service.updateTripProgress("BK-042", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("giảm trừ");
        verify(bookingRepository, never()).save(any(Booking.class));
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
        when(bookingRepository.findById(42)).thenReturn(Optional.of(bookingOwnedByCustomer()));
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
                .containsEntry("text", "Khách đã bổ sung hồ sơ và gửi lại Kiểm dịch viên thẩm định");
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
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        when(quotationRepository.findTopByBookingIdOrderBySentAtDesc(42)).thenReturn(Optional.of(quotation));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = service.rejectQuote(
                "BK-042", "phuc", RejectQuoteRequest.builder().reason("  Giá vượt ngân sách  ").build());

        assertThat(response.getStatus()).isEqualTo("cancelled");
        assertThat(quotation.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getHistory()).hasSize(1);
        assertThat(response.getHistory().get(0))
                .containsEntry("actor", "phuc")
                .containsEntry("text", "Từ chối báo giá: Giá vượt ngân sách. Đơn đã hủy, giải phóng xe và kíp xe.");
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
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        when(quotationRepository.findTopByBookingIdOrderBySentAtDesc(42)).thenReturn(Optional.empty());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = service.rejectQuote("42", "phuc", null);

        assertThat(response.getStatus()).isEqualTo("cancelled");
        assertThat(response.getHistory().get(0).get("text"))
                .isEqualTo("Từ chối báo giá: Khách hàng không đồng ý báo giá. Đơn đã hủy, giải phóng xe và kíp xe.");
        verify(quotationRepository, never()).save(any(Quotation.class));
        verify(bookingVehicleRepository).deleteByBookingID_Id(42);
        verify(bookingAssignmentRepository).deleteByBookingID_Id(42);
    }

    @Test
    void rejectQuote_rejectsBookingNotWaitingForDeposit() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("under_review");
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));

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
    void sendQuote_computesTheTotalsItselfAndIgnoresTheOnesSent() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("pending_commercial");
        booking.setDetailsJson("{\"medical\":{\"status\":\"approved\"},\"plan\":{\"at\":1},"
                + "\"trips\":[{\"tripId\":\"TRP-042-1\",\"vehicleId\":\"VH-001\",\"driverId\":\"DR-01\",\"escortId\":\"ES-01\",\"horseIds\":[]}]}");
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
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
                .containsEntry("text", "Từ chối đơn: Hồ sơ không đầy đủ");
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
                .horses(List.of(BookingHorseDto.builder().horseId(horseId).build()))
                .build();
    }

    @Test
    void getBookingById_tellsTheCustomerWhatTheyCanDo() {
        Booking booking = bookingOwnedByCustomer();
        booking.setStatus("ready_for_pickup");
        booking.setDetailsJson("{\"payment\":{\"amount\":3000000}}");
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));

        BookingResponse beforeBalance = service.getBookingById(42, "phuc");
        assertThat(beforeBalance.getCanCancel()).isTrue();
        assertThat(beforeBalance.getCanPayBalance()).isTrue();
        assertThat(beforeBalance.getCancelPenalty()).isEqualByComparingTo("3000000");

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
