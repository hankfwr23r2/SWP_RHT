package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.settlement.ExpenseItemDto;
import com.example.racehorse_transport.dto.settlement.IssueSettlementRequest;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.dto.settlement.RatingDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettlementServiceImplTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final QuotationRepository quotationRepository = mock(QuotationRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final StaffRepository staffRepository = mock(StaffRepository.class);
    private final com.example.racehorse_transport.service.BillMailService billMailService = org.mockito.Mockito.mock(com.example.racehorse_transport.service.BillMailService.class);
    private SettlementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SettlementServiceImpl(
                bookingRepository, paymentRepository, quotationRepository,
                vehicleRepository, staffRepository, new ObjectMapper(), billMailService, org.mockito.Mockito.mock(com.example.racehorse_transport.service.StaffNotifier.class));
    }

    @Test
    void holdForVnpayPreservesPrecisionOfStoredSettlementAmount() {
        Booking booking = Booking.builder()
                .id(12)
                .status("settlement_issued")
                .detailsJson("{\"settlement\":{\"total\":9007199254740993.25,\"items\":[]}}")
                .build();

        BigDecimal amount = service.holdForVnpay(booking, (RatingDto) null);

        assertThat(amount).isEqualByComparingTo("9007199254740993.25");
    }

    @Test
    void issueSettlementRejectsTotalBeyondSupportedPaymentRange() {
        Booking booking = Booking.builder().id(12).detailsJson("{}").build();
        when(bookingRepository.findById(12)).thenReturn(Optional.of(booking));
        IssueSettlementRequest request = IssueSettlementRequest.builder()
                .items(List.of(ExpenseItemDto.builder()
                        .amount(new BigDecimal("111111111111111100"))
                        .payer("customer")
                        .build()))
                .build();

        assertThatThrownBy(() -> service.issueSettlement("12", request, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vượt quá giới hạn");
        verify(bookingRepository, never()).save(any());
    }
}
