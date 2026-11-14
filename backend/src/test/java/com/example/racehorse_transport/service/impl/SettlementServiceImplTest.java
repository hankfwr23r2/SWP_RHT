package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.settlement.*;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.StaffNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SettlementServiceImplTest {
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final StaffNotifier notifier = mock(StaffNotifier.class);
    private final ObjectMapper json = new ObjectMapper();
    private SettlementServiceImpl service;
    private Booking booking;

    @BeforeEach
    void setUp() {
        service = new SettlementServiceImpl(bookings, incidents, mock(StaffRepository.class), json, notifier);
        booking = Booking.builder().id(12).status("delivered_pending_settlement").detailsJson("{}").build();
        when(bookings.findByIdForUpdate(12)).thenReturn(Optional.of(booking));
        when(bookings.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Incident expenseLedger() {
        Incident incident = Incident.builder().id(4).bookingID(booking).actualCost(new BigDecimal("800000")).build();
        incident.setExpensesJson("""
            [{"id":"vet","label":"Thú y","amount":500000,"payer":"carrier","photo":"vet.jpg"},
             {"id":"stable","label":"Lưu chuồng","amount":300000,"payer":"carrier","photo":"stable.jpg"}]
            """);
        return incident;
    }

    @Test
    void repeatedSubmissionCannotCreateADuplicateReview() {
        service.submitExpenses("12", null, "driver");
        assertThat(booking.getStatus()).isEqualTo("expenses_submitted");
        assertThatThrownBy(() -> service.submitExpenses("12", null, "driver")).hasMessageContaining("đã gửi");
        verify(bookings, times(1)).save(any());
        verify(notifier, times(1)).toManagers(any(), any(), any(), any());
    }

    @Test
    void approvalUsesTheLedgerOnceWithoutCustomerPaymentOrVehicleRelease() throws Exception {
        booking.setStatus("expenses_submitted");
        booking.setDetailsJson("""
            {"trips":[{"tripId":"T","vehicleId":"1","homeReturn":{"eta":9999999999999}}],"settlement":{"total":800000}}
            """);
        when(incidents.findByBookingID_Id(12)).thenReturn(List.of(expenseLedger()));
        var response = service.issueSettlement("12", IssueSettlementRequest.builder().build(), "manager");
        assertThat(response.getTotalCompanyCost()).isEqualByComparingTo("800000");
        assertThat(response.getItems()).hasSize(2);
        assertThat(booking.getStatus()).isEqualTo("completed");
        var stored = json.readTree(booking.getDetailsJson());
        assertThat(stored.path("trips").get(0).path("homeReturn").has("backAt")).isFalse();
        assertThat(stored.path("settlement").has("dueAt")).isFalse();
        assertThat(stored.path("settlement").has("paid")).isFalse();
    }

    @Test
    void managerCannotAssignAnyExpenseToTheCustomer() {
        booking.setStatus("expenses_submitted");
        var request = IssueSettlementRequest.builder().payers(java.util.Map.of("vet", "customer")).build();
        assertThatThrownBy(() -> service.issueSettlement("12", request, "manager"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("không được chuyển");
        verify(bookings, never()).save(any());
    }

    @Test
    void anEstimateWithoutAReceiptIsNotAnActualCompanyExpense() {
        booking.setStatus("expenses_submitted");
        when(incidents.findByBookingID_Id(12)).thenReturn(List.of(Incident.builder().id(1)
                .estimatedCost(new BigDecimal("1500000")).build()));
        assertThat(service.issueSettlement("12", null, "manager").getTotalCompanyCost()).isZero();
    }

    @Test
    void anotherCustomerCannotRateOrCloseTheOrder() {
        User user = new User(); user.setUsername("owner");
        Customer owner = new Customer(); owner.setUser(user); booking.setCustomerID(owner);
        booking.setStatus("completed");
        assertThatThrownBy(() -> service.submitRating("12", RatingDto.builder().stars(5).build(), "other"))
                .isInstanceOf(AccessDeniedException.class);
        verify(bookings, never()).save(any());
    }

    @Test
    void aLegacyReceiptedReviewWithoutAnIncidentLedgerIsPreserved() {
        booking.setStatus("expenses_submitted");
        booking.setDetailsJson("""
            {"settlement":{"items":[{"description":"Legacy vet invoice","amount":300000,"photo":"vet.jpg","payer":"customer"}]}}
            """);
        var response = service.issueSettlement("12", null, "manager");
        assertThat(response.getTotalCompanyCost()).isEqualByComparingTo("300000");
        assertThat(response.getItems()).extracting(ExpenseItemDto::getPayer).containsExactly("carrier");
    }

    @Test
    void legacyUnpaidSettlementBecomesInternalReviewAndMigrationIsIdempotent() throws Exception {
        booking.setStatus("payment_overdue");
        Incident incident = expenseLedger(); incident.setCostBearer("CUSTOMER");
        when(incidents.findAll()).thenReturn(List.of(incident));
        when(incidents.findByBookingID_Id(12)).thenReturn(List.of(incident));
        when(bookings.findByStatusOrderByBookingDateDesc("payment_overdue"))
                .thenAnswer(i -> "payment_overdue".equals(booking.getStatus()) ? List.of(booking) : List.of());
        assertThat(service.migrateLegacySettlements()).isEqualTo(2);
        assertThat(incident.getCostBearer()).isEqualTo("COMPANY");
        assertThat(booking.getStatus()).isEqualTo("expenses_submitted");
        assertThat(json.readTree(booking.getDetailsJson()).path("settlement").path("internal").asBoolean()).isTrue();
        assertThat(service.migrateLegacySettlements()).isZero();
    }
}
