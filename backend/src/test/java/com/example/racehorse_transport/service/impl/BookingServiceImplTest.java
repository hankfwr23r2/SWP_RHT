package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.booking.CreateBookingRequest;
import com.example.racehorse_transport.dto.booking.PartyContactDto;
import com.example.racehorse_transport.dto.booking.RejectQuoteRequest;
import com.example.racehorse_transport.dto.manager.RejectBookingRequest;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.User;
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
                tripDocumentRepository);

        User user = new User();
        user.setId(7);
        user.setUsername("phuc");
        user.setEmail("phuc@example.com");
        customer = new Customer();
        customer.setId(7);
        customer.setUser(user);
        customer.setFullName("Phuc");
    }

    @Test
    void createBooking_savesPendingBookingAndAuditLog() {
        Instant departure = Instant.parse("2027-01-15T10:00:00Z");
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
    void getBookingById_rejectsAnotherCustomer() {
        when(bookingRepository.findById(42)).thenReturn(Optional.of(bookingOwnedByCustomer()));

        assertThatThrownBy(() -> service.getBookingById(42, "another-customer"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("không có quyền xem đơn hàng này");
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
}
