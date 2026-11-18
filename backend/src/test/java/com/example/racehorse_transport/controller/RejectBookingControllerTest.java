package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.manager.RejectBookingRequestDto;
import com.example.racehorse_transport.service.BookingQueryService;
import com.example.racehorse_transport.service.BookingService;
import com.example.racehorse_transport.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RejectBookingControllerTest {

    private final BookingService bookingService = mock(BookingService.class);

    @Test
    void managerRejectionUsesTheSignedInManagerNotTheBody() {
        RejectBookingRequestDto body = RejectBookingRequestDto.builder().by("someone else").role("coordinator").reason("Không đủ xe").build();

        new ManagerBookingController(bookingService, mock(PaymentService.class)).rejectBooking("BK-001", body, new TestingAuthenticationToken("manager01", null, "ROLE_MANAGER"));

        ArgumentCaptor<RejectBookingRequestDto> sent = ArgumentCaptor.forClass(RejectBookingRequestDto.class);
        verify(bookingService).rejectBooking(eq("BK-001"), sent.capture());
        assertThat(sent.getValue().getRole()).isEqualTo("manager");
        assertThat(sent.getValue().getBy()).isEqualTo("manager01");
        assertThat(sent.getValue().getReason()).isEqualTo("Không đủ xe");
    }

    @Test
    void coordinatorRejectionAlwaysActsAsCoordinator() {
        RejectBookingRequestDto body = RejectBookingRequestDto.builder().by("manager01").role("manager").reason("Không có lộ trình").build();

        new CoordinatorBookingController(bookingService, org.mockito.Mockito.mock(BookingQueryService.class)).rejectBooking("BK-002", body, new TestingAuthenticationToken("coordinator01", null, "ROLE_COORDINATOR"));

        ArgumentCaptor<RejectBookingRequestDto> sent = ArgumentCaptor.forClass(RejectBookingRequestDto.class);
        verify(bookingService).rejectBooking(eq("BK-002"), sent.capture());
        assertThat(sent.getValue().getRole()).isEqualTo("coordinator");
        assertThat(sent.getValue().getBy()).isEqualTo("coordinator01");
    }
}
