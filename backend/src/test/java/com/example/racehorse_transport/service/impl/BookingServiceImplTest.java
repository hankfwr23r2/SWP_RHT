package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.booking.CreateBookingRequest;
import com.example.racehorse_transport.dto.booking.PartyContactDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.User;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
}