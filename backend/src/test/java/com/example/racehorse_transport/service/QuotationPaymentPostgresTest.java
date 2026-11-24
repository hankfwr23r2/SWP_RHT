package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.dto.booking.CancelBookingRequestDto;
import com.example.racehorse_transport.dto.booking.CompanyCancelBookingRequestDto;
import com.example.racehorse_transport.dto.booking.ManagerCancelBookingRequestDto;
import com.example.racehorse_transport.dto.payment.MarkRefundedRequestDto;
import com.example.racehorse_transport.dto.quotation.QuotationAdjustmentRequestDto;
import com.example.racehorse_transport.scheduler.DocumentReminderScheduler;
import com.example.racehorse_transport.scheduler.SettlementScheduler;
import com.example.racehorse_transport.dto.quotation.QuotationRejectRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.policy.QuotationPolicy;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import com.example.racehorse_transport.util.VnpayUtil;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.UriComponentsBuilder;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "vnpay.tmn-code=A7TEST",
        "vnpay.hash-secret=a7-test-signature",
        "vnpay.quotation-expiry-scan-ms=3600000"
})
class QuotationPaymentPostgresTest {
    private static final Instant START = Instant.parse("2027-01-14T10:00:00Z");
    private static final DateTimeFormatter GATEWAY_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(QuotationPolicy.BUSINESS_ZONE);

    @Autowired private QuotationService quotations;
    @Autowired private BookingHorseService horses;
    @Autowired private PaymentService payments;
    @Autowired private BookingCancellationService cancellations;
    @Autowired private RefundService refunds;
    @Autowired private SettlementService settlements;
    @Autowired private SettlementScheduler settlementScheduler;
    @Autowired private DocumentReminderScheduler reminderScheduler;
    @MockitoSpyBean private DocumentCompletenessService documentCompleteness;
    @Autowired private PaymentCheckoutService checkout;
    @Autowired private PaymentResultService results;
    @Autowired private PaymentReadinessService readiness;
    @Autowired private TestData data;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtUtils jwt;
    @Autowired private MockMvc mvc;
    @MockitoBean private Clock clock;
    @MockitoBean private PricingRouteService maps;

    @BeforeEach
    void useFixedTime() {
        when(clock.instant()).thenReturn(START);
    }

    @Test
    void managerDraftUsesSavedAddressesAndCallsMapWithoutTransaction() {
        var fixture = fixture(BookingType.LOCAL, BookingStatus.QUOTING);
        data.bookingAddress(fixture.booking(), AddressRole.PICKUP);
        data.bookingAddress(fixture.booking(), AddressRole.DELIVERY);
        long horse = attachHorse(fixture, "WIDE");
        confirmWaiver(fixture, horse);
        when(maps.route(any(), any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new PricingRouteService.RoadEstimate(new BigDecimal("35"), 600);
        });
        var draft = quotations.createDraft(fixture.booking().getId());
        assertThat(draft.status()).isEqualTo(QuotationStatus.DRAFT);
        assertThat(draft.lines()).anyMatch(line -> line.lineType() == QuotationLineType.DIRECT);
        assertThat(draft.lines()).anyMatch(line -> line.lineType() == QuotationLineType.STALL
                && line.bookingHorseId().equals(horse) && line.priceItemId() != null);
        assertThat(quotations.createDraft(fixture.booking().getId()).id()).isEqualTo(draft.id());
    }

    @Test
    void snapshotsInsurerPremiumAndRejectsSendingAfterTheInsurerDeadline() {
        var fixture = fixture(BookingType.LOCAL, BookingStatus.QUOTING);
        data.bookingAddress(fixture.booking(), AddressRole.PICKUP);
        data.bookingAddress(fixture.booking(), AddressRole.DELIVERY);
        long horse = attachHorse(fixture, "STANDARD");
        Instant insuranceDeadline = START.plusSeconds(120);
        jdbc.update("""
                insert into horse_insurance (booking_horse_id, coverage_option, insurer_party_id, coverage_amount,
                    premium_amount, currency_code, quote_expires_at, status, confirmed_by, confirmed_at)
                select ?, 'ARRANGED', p.id, 100000, 2500, 'VND', ?, 'CONFIRMED', ?, now()
                from party p where p.display_name = 'Công ty bảo hiểm (mẫu)'
                """, horse, java.sql.Timestamp.from(insuranceDeadline), fixture.managerId());
        when(maps.route(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PricingRouteService.RoadEstimate(new BigDecimal("35"), 600));
        var draft = quotations.createDraft(fixture.booking().getId());
        var insurance = draft.lines().stream().filter(line -> line.lineType() == QuotationLineType.INSURANCE).toList();
        assertThat(insurance).hasSize(1);
        assertThat(insurance.getFirst().priceItemId()).isNull();
        assertThat(insurance.getFirst().bookingHorseId()).isEqualTo(horse);
        assertThat(insurance.getFirst().unitPrice()).isEqualByComparingTo("2500");
        assertThat(insurance.getFirst().passThrough()).isTrue();

        when(clock.instant()).thenReturn(insuranceDeadline);
        assertThatThrownBy(() -> quotations.send(draft.id(), fixture.manager()))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("đã hết hạn");
        assertThat(bookingStatus(fixture)).isEqualTo("QUOTING");
    }

    @Test
    void draftMadeBeforeAScheduleChangeCannotBeSent() {
        var fixture = fixture(BookingType.INTERPROVINCE, BookingStatus.QUOTING);
        reserveWideHorse(fixture);
        confirmWaiver(fixture, jdbc.queryForObject("select id from booking_horse where booking_id = ?", Long.class,
                fixture.booking().getId()));
        long quote = quote(fixture, QuotationStatus.DRAFT, START.plusSeconds(900), "10000", "3000");
        addAdjustmentLine(quote, 1, "10000");
        jdbc.update("update departure set departure_date = departure_date + 7 where id = (select departure_id from booking b join quotation q on q.booking_id = b.id where q.id = ?)", quote);
        assertThatThrownBy(() -> quotations.send(quote, fixture.manager())).isInstanceOf(BookingRuleException.class)
                .hasMessageContaining("đã thay đổi");
        assertThat(bookingStatus(fixture)).isEqualTo("QUOTING");
    }

    @Test
    void sendingANewQuoteCancelsTheDepositOfTheOldOne() {
        var fixture = fixture(BookingType.INTERPROVINCE, BookingStatus.AWAITING_DEPOSIT);
        reserveWideHorse(fixture);
        confirmWaiver(fixture, jdbc.queryForObject("select id from booking_horse where booking_id = ?", Long.class,
                fixture.booking().getId()));
        long old = quote(fixture, QuotationStatus.SENT, START.plusSeconds(900), "10000", "4000");
        long deposit = acceptTwice(fixture, old);
        jdbc.update("update booking set status = 'QUOTING' where id = ?", fixture.booking().getId());
        long quote = quote(fixture, QuotationStatus.DRAFT, START.plusSeconds(900), "20000", "6000");
        addAdjustmentLine(quote, 1, "20000");

        quotations.send(quote, fixture.manager());

        assertThat(jdbc.queryForObject("select status from payment where id = ?", String.class, deposit)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select status from quotation where id = ?", String.class, old)).isEqualTo("SUPERSEDED");
    }

    @Test
    void quoteRejectsZeroAndEditsAfterSendingAndRetainsTheReservationOnCustomerReject() {
        var fixture = fixture(BookingType.INTERPROVINCE, BookingStatus.QUOTING);
        var departure = reserveWideHorse(fixture);
        confirmWaiver(fixture, jdbc.queryForObject("select id from booking_horse where booking_id = ?", Long.class,
                fixture.booking().getId()));
        long quote = quote(fixture, QuotationStatus.DRAFT, START.plusSeconds(900), "10000", "3000");
        addAdjustmentLine(quote, 1, "10000");
        quotations.adjust(quote, new QuotationAdjustmentRequestDto(new BigDecimal("-10000"), "Giảm giá"));
        assertThatThrownBy(() -> quotations.send(quote, fixture.manager())).isInstanceOf(BookingRuleException.class);
        assertThat(bookingStatus(fixture)).isEqualTo("QUOTING");
        quotations.adjust(quote, new QuotationAdjustmentRequestDto(new BigDecimal("10000"), "Sửa giá"));
        var sent = quotations.send(quote, fixture.manager());
        assertThat(sent.status()).isEqualTo(QuotationStatus.SENT);
        assertThatThrownBy(() -> quotations.adjust(quote, new QuotationAdjustmentRequestDto(BigDecimal.ONE, "Sửa giá")))
                .isInstanceOf(BookingRuleException.class);
        quotations.reject(quote, new QuotationRejectRequestDto("Cần đổi lịch"), fixture.customer());
        assertThat(bookingStatus(fixture)).isEqualTo("QUOTING");
        assertThat(bookedSeats(departure)).isEqualTo(2);
    }

    @Test
    void expiryAndIpnSerializeAndHonorOnTimeFundsInTheGraceWindow() throws Exception {
        var fixture = fixture(BookingType.INTERPROVINCE, BookingStatus.AWAITING_DEPOSIT);
        long departure = reserveWideHorse(fixture);
        var deadline = START.plusSeconds(120);
        long quote = quote(fixture, QuotationStatus.SENT, deadline, "10000", "4000");
        long payment = acceptTwice(fixture, quote);
        var reference = checkout(payment, fixture);
        var callback = callback(reference, "804901", 4000, deadline.minusSeconds(1), true);
        when(clock.instant()).thenReturn(deadline.plusSeconds(10));

        race(() -> { quotations.expire(quote); return null; }, () -> results.receive(callback));

        assertThat(bookingStatus(fixture)).isEqualTo("CONFIRMED");
        assertThat(bookedSeats(departure)).isEqualTo(2);
        assertThat(gatewayRows(payment)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select waybill_number from booking where id = ?", String.class,
                fixture.booking().getId())).isNotBlank();
    }

    @Test
    void simultaneousIpnsRecordOneGatewayResultAndOneBookingTransition() throws Exception {
        var fixture = fixture(BookingType.LOCAL, BookingStatus.AWAITING_DEPOSIT);
        long quote = quote(fixture, QuotationStatus.SENT, START.plusSeconds(120), "9000", "3000");
        long payment = acceptTwice(fixture, quote);
        var callback = callback(checkout(payment, fixture), "804902", 3000, START, true);
        var codes = race(() -> results.receive(callback).code(), () -> results.receive(callback).code());
        assertThat(codes).containsExactlyInAnyOrder("00", "02");
        assertThat(gatewayRows(payment)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from booking_status_history where booking_id = ? and to_status = 'CONFIRMED'
                """, Integer.class, fixture.booking().getId())).isEqualTo(1);
    }

    @Test
    void expiryReleasesWideSeatsOnceAndRecordsLateFundsWithoutConfirming() {
        var fixture = fixture(BookingType.INTERPROVINCE, BookingStatus.AWAITING_DEPOSIT);
        long departure = reserveWideHorse(fixture);
        var deadline = START.plusSeconds(120);
        long quote = quote(fixture, QuotationStatus.SENT, deadline, "10000", "4000");
        long payment = acceptTwice(fixture, quote);
        String reference = checkout(payment, fixture);
        when(clock.instant()).thenReturn(deadline.plusSeconds(360));
        quotations.expire(quote);
        quotations.expire(quote);
        assertThat(bookedSeats(departure)).isZero();
        assertThat(jdbc.queryForObject("select departure_id from booking where id = ?", Long.class,
                fixture.booking().getId())).isNull();
        var late = callback(reference, "804903", 4000, deadline.minusSeconds(1), true);
        var shown = results.receiveReturn(late);
        assertThat(shown.success()).isFalse();
        assertThat(shown.needsReview()).isTrue();
        assertThat(shown.message()).contains("liên hệ hoàn tiền");
        assertThat(shown.amount()).isEqualByComparingTo("4000");
        assertThat(results.receive(late).code()).isEqualTo("02");
        assertThat(jdbc.queryForObject("""
                select count(*) from notification where booking_id = ? and title = 'Đối soát VNPAY'
                and message like '%4000 VND%804903%ngoài hạn%'
                """, Integer.class, fixture.booking().getId())).isGreaterThanOrEqualTo(1);
        assertThat(gatewayRows(payment)).isEqualTo(1);
        assertThat(bookingStatus(fixture)).isEqualTo("QUOTE_EXPIRED");
    }

    @Test
    void oldBalanceLinkCreditsItsSignedAmountAfterExclusion() {
        var fixture = fixture(BookingType.INTERPROVINCE, BookingStatus.AWAITING_DEPOSIT);
        long departure = reserveWideHorse(fixture);
        long excluded = jdbc.queryForObject("select id from booking_horse where booking_id = ?", Long.class,
                fixture.booking().getId());
        long quote = quote(fixture, QuotationStatus.SENT, START.plusSeconds(120), "10000", "4000");
        addAdjustmentLine(quote, 1, "7000");
        jdbc.update("""
                insert into quotation_line (quotation_id, line_number, line_type, billable_service_id, price_item_id,
                    booking_horse_id, description, quantity, unit, unit_price, is_pass_through)
                select ?, 2, 'LINEHAUL', pi.billable_service_id, pi.id, ?, 'Cước', 1, 'PER_STALL', 3000, false
                from price_item pi join billable_service s on s.id = pi.billable_service_id
                where s.layer = 'LINEHAUL' and pi.price_list_id = (select price_list_id from quotation where id = ?)
                order by pi.id limit 1
                """, quote, excluded, quote);
        long deposit = acceptTwice(fixture, quote);
        assertThat(results.receive(callback(checkout(deposit, fixture), "804904", 4000, START, true)).code()).isEqualTo("00");
        payments.requestBalance(fixture.booking().getId(), START);
        payments.requestBalance(fixture.booking().getId(), START.plusSeconds(30));
        var balance = payments.payments(fixture.booking().getId(), fixture.customer()).stream()
                .filter(p -> p.type() == PaymentType.BALANCE).toList();
        assertThat(balance).hasSize(1);
        assertThat(balance.getFirst().amount()).isEqualByComparingTo("6000");
        assertThat(balance.getFirst().dueAt()).isEqualTo(START.plusSeconds(1800));
        String oldLink = checkout(balance.getFirst().id(), fixture);
        assertThat(checkout.hasPendingBalanceConfirmation(fixture.booking().getId())).isTrue();
        assertThat(payments.payments(fixture.booking().getId(), fixture.customer()).getLast().confirmationUntil())
                .isEqualTo(START.plusSeconds(35 * 60));
        // Khách bỏ dở link: chỉ chờ thêm 5 phút sau hạn trả
        when(clock.instant()).thenReturn(START.plusSeconds(35 * 60 - 1));
        assertThat(checkout.hasPendingBalanceConfirmation(fixture.booking().getId())).isTrue();
        when(clock.instant()).thenReturn(START.plusSeconds(35 * 60));
        assertThat(checkout.hasPendingBalanceConfirmation(fixture.booking().getId())).isFalse();
        when(clock.instant()).thenReturn(START);

        horses.exclude(excluded, "Không đạt kiểm tra sức khỏe", fixture.managerId());
        horses.exclude(excluded, "Gửi lại", fixture.managerId());
        assertThat(bookedSeats(departure)).isZero();
        var visible = quotations.quotations(fixture.booking().getId(), fixture.customer(), false);
        assertThat(visible).extracting(q -> q.status()).containsExactly(QuotationStatus.SUPERSEDED, QuotationStatus.ACCEPTED);
        assertThat(visible.getLast().totalAmount()).isEqualByComparingTo("7000");
        assertThat(readiness.isPaidInFull(fixture.booking().getId())).isFalse();

        assertThat(results.receive(callback(oldLink, "804905", 6000, START, true)).code()).isEqualTo("00");
        assertThat(checkout.hasPendingBalanceConfirmation(fixture.booking().getId())).isFalse();
        assertThat(readiness.isPaidInFull(fixture.booking().getId())).isTrue();
        var updated = payments.payments(fixture.booking().getId(), fixture.customer());
        assertThat(updated.stream().filter(p -> p.type() == PaymentType.BALANCE).findFirst().orElseThrow().amount())
                .isEqualByComparingTo("6000");
        assertThat(updated.stream().filter(p -> p.type() == PaymentType.REFUND).toList()).singleElement().satisfies(refund -> {
            assertThat(refund.amount()).isEqualByComparingTo("3000");
            assertThat(refund.status()).isEqualTo(PaymentStatus.PENDING);
        });
    }

    @Test
    void validatesHttpRolesOwnershipStateAndSignatureAndHandlesFailedResultWithoutTransactionNumber() throws Exception {
        var fixture = fixture(BookingType.LOCAL, BookingStatus.AWAITING_DEPOSIT);
        long quote = quote(fixture, QuotationStatus.SENT, START.plusSeconds(120), "9000", "3000");
        String other = data.unique("a7-other");
        data.customer(other);
        mvc.perform(post("/quotations/{id}/accept", quote).header(HttpHeaders.AUTHORIZATION, auth(fixture.manager(), "MANAGER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/quotations/{id}/accept", quote).header(HttpHeaders.AUTHORIZATION, auth(other, "CUSTOMER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/payments/vnpay/ipn").param("vnp_SecureHash", "invalid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("97"));

        long payment = acceptTwice(fixture, quote);
        String reference = checkout(payment, fixture);
        var wrongAmount = callback(reference, "804906", 2000, START, true);
        assertThat(results.receive(wrongAmount).code()).isEqualTo("04");
        var failure = callback(reference, null, 3000, START, false);
        assertThat(results.receive(failure).code()).isEqualTo("00");
        assertThat(results.receive(failure).code()).isEqualTo("02");
        assertThat(bookingStatus(fixture)).isEqualTo("AWAITING_DEPOSIT");
        when(clock.instant()).thenReturn(START.plusSeconds(120));
        mvc.perform(post("/payments/{id}/checkout", payment)
                        .header(HttpHeaders.AUTHORIZATION, auth(fixture.customer(), "CUSTOMER")))
                .andExpect(status().isConflict());
    }

    @Test
    void cancellationAndSuccessfulDepositIpnUseTheSameBookingLock() throws Exception {
        var fixture = fixture(BookingType.INTERPROVINCE, BookingStatus.AWAITING_DEPOSIT);
        long departure = reserveWideHorse(fixture);
        var deadline = START.plusSeconds(120);
        long quote = quote(fixture, QuotationStatus.SENT, deadline, "10000", "4000");
        long payment = acceptTwice(fixture, quote);
        var reference = checkout(payment, fixture);
        var callback = callback(reference, "804942", 4000, START, true);

        var outcomes = race(() -> {
            try {
                cancellations.cancelByCustomer(fixture.booking().getId(), fixture.customer(), new CancelBookingRequestDto("Đổi lịch"));
                return "cancelled";
            } catch (BookingRuleException exception) {
                return "conflict";
            }
        }, () -> results.receive(callback).code());

        assertThat(outcomes.getLast()).isEqualTo("00");
        // Link đang chờ không chặn hủy. Hủy trước thì tiền về sau chỉ lưu để đối soát, không mất giao dịch
        assertThat(outcomes.getFirst()).isEqualTo("cancelled");
        assertThat(bookingStatus(fixture)).isEqualTo("CANCELLED");
        assertThat(bookedSeats(departure)).isZero();
        assertThat(jdbc.queryForObject("select status from payment where id = ?", String.class, payment)).isIn("PAID", "CANCELLED");
        assertThat(gatewayRows(payment)).isEqualTo(1);
    }

    @Test
    void lateBalanceIpnAndPickupCancellationCannotBothApply() throws Exception {
        var fixture = fixture(BookingType.LOCAL, BookingStatus.CONFIRMED);
        long booking = fixture.booking().getId();
        long quote = quote(fixture, QuotationStatus.ACCEPTED, START.plusSeconds(120), "10000", "4000");
        paidDeposit(booking, quote, "4000");
        payments.requestBalance(booking, START);
        long balance = payments.payments(booking, fixture.customer()).stream()
                .filter(p -> p.type() == PaymentType.BALANCE).findFirst().orElseThrow().id();
        String reference = checkout(balance, fixture);
        when(clock.instant()).thenReturn(START.plusSeconds(36 * 60L));

        var outcomes = race(() -> {
            try {
                cancellations.cancelUnpaidAtPickup(booking, fixture.managerId());
                return "cancelled";
            } catch (BookingRuleException exception) {
                return "conflict";
            }
        }, () -> results.receive(callback(reference, "804976", 6000, START, true)).code());

        assertThat(outcomes.getLast()).isEqualTo("00");
        String balanceStatus = jdbc.queryForObject("select status from payment where id = ?", String.class, balance);
        if ("cancelled".equals(outcomes.getFirst())) {
            assertThat(bookingStatus(fixture)).isEqualTo("CANCELLED");
            assertThat(balanceStatus).isEqualTo("CANCELLED");
        } else {
            assertThat(outcomes.getFirst()).isEqualTo("conflict");
            assertThat(bookingStatus(fixture)).isEqualTo("CONFIRMED");
            assertThat(balanceStatus).isEqualTo("PAID");
        }
    }

    @Test
    void companyCancellationReleasesCancelledDepartureAndConfirmationIsSingleUse() {
        var fixture = fixture(BookingType.INTERPROVINCE, BookingStatus.CONFIRMED);
        long departure = reserveWideHorse(fixture);
        long booking = fixture.booking().getId();
        long quote = quote(fixture, QuotationStatus.ACCEPTED, START.plusSeconds(120), "10000", "4000");
        long deposit = paidDeposit(booking, quote, "4000");
        addServiceLine(quote, 1, "GOV_FEE", "1000");
        jdbc.update("update departure set status = 'CANCELLED' where id = ?", departure);

        var cancelled = cancellations.cancelForCompany(booking, fixture.manager(), new CompanyCancelBookingRequestDto("Hủy chuyến"));
        assertThat(cancelled.refundable()).isEqualByComparingTo("4000");
        assertThat(cancelled.forfeited()).isEqualByComparingTo("0");
        assertThat(bookedSeats(departure)).isZero();
        long refund = jdbc.queryForObject("""
                select id from payment where booking_id = ? and payment_type = 'REFUND' and refunded_payment_id = ?
                """, Long.class, booking, deposit);
        var request = new MarkRefundedRequestDto(data.unique("refund-transfer"));
        assertThat(refunds.markRefunded(refund, fixture.manager(), request, null).status()).isEqualTo(PaymentStatus.PAID);
        assertThatThrownBy(() -> refunds.markRefunded(refund, fixture.manager(), request, null))
                .isInstanceOf(BookingRuleException.class);
    }

    @Test
    void settlementRunsTwiceAndWritesOneCompletedHistoryRow() {
        var fixture = fixture(BookingType.LOCAL, BookingStatus.DELIVERED);
        long booking = fixture.booking().getId();
        long quote = quote(fixture, QuotationStatus.ACCEPTED, START.plusSeconds(120), "10000", "10000");
        paidDeposit(booking, quote, "10000");
        jdbc.update("""
                insert into booking_status_history (booking_id, from_status, to_status, changed_at)
                values (?, 'IN_OPERATION', 'DELIVERED', ?)
                """, booking, java.sql.Timestamp.from(START.minusSeconds(8 * 86400L)));
        settlementScheduler.closeDeliveredBookings();
        settlementScheduler.closeDeliveredBookings();

        assertThat(bookingStatus(fixture)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("""
                select count(*) from booking_status_history where booking_id = ? and to_status = 'COMPLETED' and changed_by is null
                """, Integer.class, booking)).isEqualTo(1);
    }

    @Test
    void documentReminderRunsTwiceAndWritesOneNotification() {
        var fixture = fixture(BookingType.INTERPROVINCE, BookingStatus.CONFIRMED);
        long departure = reserveWideHorse(fixture);
        long booking = fixture.booking().getId();
        var now = jdbc.queryForObject("select now()", java.sql.Timestamp.class).toInstant();
        when(clock.instant()).thenReturn(now);
        jdbc.update("update departure set booking_cutoff_at = ?, planned_departure_at = ? where id = ?",
                java.sql.Timestamp.from(now.minusSeconds(3600)), java.sql.Timestamp.from(now.plusSeconds(2 * 86400L)), departure);
        doReturn(false).when(documentCompleteness).isComplete(booking);

        reminderScheduler.sendReminders();
        reminderScheduler.sendReminders();

        assertThat(jdbc.queryForObject("""
                select count(*) from notification where booking_id = ? and title = 'Bổ sung giấy tờ trước chuyến'
                """, Integer.class, booking)).isEqualTo(1);
    }

    @Test
    void ratingApiChecksRoleOwnerAndDuplicate() throws Exception {
        var fixture = fixture(BookingType.LOCAL, BookingStatus.DELIVERED);
        long booking = fixture.booking().getId();
        String other = data.customer(data.unique("rating-other")).getUser().getUsername();
        String input = "{\"score\":5,\"comment\":\"Đã nhận ngựa\"}";

        mvc.perform(post("/bookings/{id}/ratings", booking).contentType("application/json").content(input)
                .header(HttpHeaders.AUTHORIZATION, auth(fixture.manager(), "MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(post("/bookings/{id}/ratings", booking).contentType("application/json").content(input)
                .header(HttpHeaders.AUTHORIZATION, auth(other, "CUSTOMER"))).andExpect(status().isNotFound());
        mvc.perform(post("/bookings/{id}/ratings", booking).contentType("application/json").content(input)
                .header(HttpHeaders.AUTHORIZATION, auth(fixture.customer(), "CUSTOMER"))).andExpect(status().isOk());
        mvc.perform(post("/bookings/{id}/ratings", booking).contentType("application/json").content(input)
                .header(HttpHeaders.AUTHORIZATION, auth(fixture.customer(), "CUSTOMER"))).andExpect(status().isConflict());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"CUSTOMER,1,0,4000", "COMPANY,1,4000,0", "AUTHORITY,1,0,4000", "AUTHORITY,2,3000,1000"})
    void cancelsAccordingToRecordedScheduleChangeCause(String cause, int misses, String expectedRefund, String expectedForfeited) {
        var fixture = fixture(BookingType.INTERPROVINCE, BookingStatus.SCHEDULE_CHANGE_REQUIRED);
        long booking = fixture.booking().getId();
        long quote = quote(fixture, QuotationStatus.ACCEPTED, START.plusSeconds(120), "10000", "4000");
        paidDeposit(booking, quote, "4000");
        addServiceLine(quote, 1, "GOV_FEE", "1000");
        for (int i = 0; i < misses; i++) {
            jdbc.update("""
                    insert into booking_status_history (booking_id, from_status, to_status, cause_type)
                    values (?, 'CONFIRMED', 'SCHEDULE_CHANGE_REQUIRED', ?)
                    """, booking, cause);
        }
        var cancelled = cancellations.cancelByCustomer(booking, fixture.customer(), new CancelBookingRequestDto("Không thể đi chuyến khác"));
        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.refundable()).isEqualByComparingTo(expectedRefund);
        assertThat(cancelled.reviewable()).isEqualByComparingTo("0");
        assertThat(cancelled.forfeited()).isEqualByComparingTo(expectedForfeited);
        assertThat(jdbc.queryForObject("select coalesce(sum(amount), 0) from payment where booking_id = ? and payment_type = 'REFUND'",
                BigDecimal.class, booking)).isEqualByComparingTo(expectedRefund);
    }

    @Test
    void requiresManagerAndDecisionWindowToReportDocumentDelay() throws Exception {
        var fixture = fixture(BookingType.INTERPROVINCE, BookingStatus.CONFIRMED);
        long booking = fixture.booking().getId();
        long departure = reserveWideHorse(fixture);
        // Danh sách đọc mọi đơn CONFIRMED trong DB test, gồm đơn mẫu không có địa chỉ
        doReturn(false).when(documentCompleteness).isComplete(org.mockito.ArgumentMatchers.anyLong());
        var plannedAt = jdbc.queryForObject("select planned_departure_at from departure where id = ?",
                java.sql.Timestamp.class, departure).toInstant();
        String input = "{\"cause\":\"AUTHORITY\",\"reason\":\"Chậm cấp giấy\"}";
        when(clock.instant()).thenReturn(plannedAt.minusSeconds(49 * 3600L));

        mvc.perform(post("/bookings/{id}/report-document-delay", booking).contentType("application/json").content(input)
                .header(HttpHeaders.AUTHORIZATION, auth(fixture.customer(), "CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(get("/bookings/missing-documents")
                .header(HttpHeaders.AUTHORIZATION, auth(fixture.manager(), "MANAGER"))).andExpect(status().isOk());
        mvc.perform(post("/bookings/{id}/report-document-delay", booking).contentType("application/json").content(input)
                .header(HttpHeaders.AUTHORIZATION, auth(fixture.manager(), "MANAGER"))).andExpect(status().isConflict());
        mvc.perform(post("/bookings/{id}/report-document-delay", Long.MAX_VALUE).contentType("application/json").content(input)
                .header(HttpHeaders.AUTHORIZATION, auth(fixture.manager(), "MANAGER"))).andExpect(status().isNotFound());

        when(clock.instant()).thenReturn(plannedAt.minusSeconds(48 * 3600L));
        mvc.perform(post("/bookings/{id}/report-document-delay", booking).contentType("application/json").content(input)
                .header(HttpHeaders.AUTHORIZATION, auth(fixture.manager(), "MANAGER"))).andExpect(status().isOk());
        assertThat(bookedSeats(departure)).isZero();
        assertThat(jdbc.queryForObject("select departure_id from booking where id = ?", Long.class, booking)).isNull();
        assertThat(jdbc.queryForObject("""
                select cause_type from booking_status_history where booking_id = ? and previous_departure_id = ?
                """, String.class, booking, departure)).isEqualTo("AUTHORITY");
    }

    @Test
    void customerCancellationForfeitsDepositWithPassThroughAndManagerCannotRefundLines() throws Exception {
        var fixture = fixture(BookingType.LOCAL, BookingStatus.CONFIRMED);
        long booking = fixture.booking().getId();
        long quote = quote(fixture, QuotationStatus.ACCEPTED, START.plusSeconds(120), "10000", "4000");
        paidDeposit(booking, quote, "4000");
        long govFee = addServiceLine(quote, 1, "GOV_FEE", "1000");
        long vetCheck = addServiceLine(quote, 2, "VET_CHECK", "500");

        var preview = cancellations.preview(booking, fixture.customer());
        assertThat(preview.forfeited()).isEqualByComparingTo("4000");
        var cancelled = cancellations.cancelByCustomer(booking, fixture.customer(), new CancelBookingRequestDto("Đổi lịch"));
        assertThat(cancelled.refundable()).isEqualByComparingTo("0");
        assertThat(cancelled.reviewable()).isEqualByComparingTo("0");
        assertThat(cancelled.forfeited()).isEqualByComparingTo("4000");
        assertThat(cancelled.reviewableQuotationLineIds()).isEmpty();

        String input = "{\"unspentQuotationLineIds\":[" + vetCheck + "]}";
        mvc.perform(post("/bookings/{id}/refund-unspent-lines", booking).contentType("application/json").content(input)
                .header(HttpHeaders.AUTHORIZATION, auth(fixture.customer(), "CUSTOMER"))).andExpect(status().isForbidden());
        for (long line : List.of(vetCheck, govFee)) {
            mvc.perform(post("/bookings/{id}/refund-unspent-lines", booking).contentType("application/json")
                            .content("{\"unspentQuotationLineIds\":[" + line + "]}")
                            .header(HttpHeaders.AUTHORIZATION, auth(fixture.manager(), "MANAGER")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Khoản thu hộ không được hoàn riêng khi hủy đơn."));
        }
        mvc.perform(post("/bookings/{id}/refund-unspent-lines", Long.MAX_VALUE).contentType("application/json").content(input)
                .header(HttpHeaders.AUTHORIZATION, auth(fixture.manager(), "MANAGER"))).andExpect(status().isNotFound());
        mvc.perform(get("/bookings/{id}/refund-review", booking)
                        .header(HttpHeaders.AUTHORIZATION, auth(fixture.manager(), "MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reviewable").value(0))
                .andExpect(jsonPath("$.data.forfeited").value(4000.0))
                .andExpect(jsonPath("$.data.reviewableQuotationLineIds").isEmpty());
        assertThat(refundRows(booking)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from quotation_line where quotation_id = ? and refund_payment_id is not null",
                Integer.class, quote)).isZero();
    }

    @Test
    void managerCancellationRejectsSelectedPassThroughLineWithoutChangingBooking() {
        var fixture = fixture(BookingType.LOCAL, BookingStatus.CONFIRMED);
        long booking = fixture.booking().getId();
        long quote = quote(fixture, QuotationStatus.ACCEPTED, START.plusSeconds(120), "10000", "4000");
        paidDeposit(booking, quote, "4000");
        long govFee = addServiceLine(quote, 1, "GOV_FEE", "1000");

        assertThatThrownBy(() -> cancellations.cancelForCustomer(booking, fixture.manager(),
                new ManagerCancelBookingRequestDto("Khách đổi lịch", List.of(govFee))))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("không được hoàn riêng");
        assertThat(bookingStatus(fixture)).isEqualTo("CONFIRMED");

        var cancelled = cancellations.cancelForCustomer(booking, fixture.manager(),
                new ManagerCancelBookingRequestDto("Khách đổi lịch", List.of()));
        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.forfeited()).isEqualByComparingTo("4000");
        assertThat(refundRows(booking)).isZero();
    }

    @Test
    void approvedConsigneeCostCanBeCollectedThroughVnpayOnce() {
        var fixture = fixture(BookingType.LOCAL, BookingStatus.DELIVERED);
        long booking = fixture.booking().getId();
        long quote = quote(fixture, QuotationStatus.ACCEPTED, START.plusSeconds(120), "10000", "10000");
        paidDeposit(booking, quote, "10000");
        long cost = jdbc.queryForObject("""
                insert into cost_item (category, description, amount, currency_code, payer_type, booking_id,
                    is_customer_consulted, incurred_at, recorded_by, approved_by, approved_at)
                values ('WAITING', 'Phí chờ', 1500, 'VND', 'CONSIGNEE', ?, true, now(), ?, ?, now()) returning id
                """, Long.class, booking, fixture.managerId(), fixture.managerId());

        var charge = settlements.createExtraCharge(booking, List.of(cost), fixture.manager());
        assertThat(charge.type()).isEqualTo(PaymentType.EXTRA_CHARGE);
        assertThat(charge.amount()).isEqualByComparingTo("1500");
        assertThat(jdbc.queryForObject("select extra_charge_payment_id from cost_item where id = ?", Long.class, cost))
                .isEqualTo(charge.id());
        assertThatThrownBy(() -> settlements.createExtraCharge(booking, List.of(cost), fixture.manager()))
                .isInstanceOf(BookingRuleException.class);

        var reference = checkout(charge.id(), fixture);
        var callback = callback(reference, "804977", 1500, START, true);
        assertThat(results.receive(callback).code()).isEqualTo("00");
        assertThat(results.receive(callback).code()).isEqualTo("02");
        assertThat(settlements.settlement(booking).outstanding()).isEqualByComparingTo("0");
    }

    private long paidDeposit(long booking, long quote, String value) {
        return jdbc.queryForObject("""
                insert into payment (booking_id, quotation_id, payment_type, amount, currency_code, payment_method, status, paid_at)
                values (?, ?, 'DEPOSIT', ?, 'VND', 'VNPAY', 'PAID', ?) returning id
                """, Long.class, booking, quote, new BigDecimal(value), java.sql.Timestamp.from(START));
    }

    private Fixture fixture(BookingType type, BookingStatus status) {
        var customer = data.customer(data.unique("a7-customer"));
        String manager = data.unique("a7-manager");
        var staff = data.staff(manager, "MANAGER");
        return new Fixture(data.booking(customer, type, status), customer.getUser().getUsername(), manager, staff.getUserId());
    }

    private long quote(Fixture fixture, QuotationStatus status, Instant expires, String total, String deposit) {
        var booking = fixture.booking();
        boolean draft = status == QuotationStatus.DRAFT;
        return jdbc.queryForObject("""
                insert into quotation (booking_id, version, price_list_id, currency_code, deposit_rate, expires_at,
                    confirmed_pickup_date, confirmed_departure_date, estimated_delivery_date, total_amount, pass_through_amount,
                    deposit_amount, status, sent_by, sent_at)
                select b.id, (select coalesce(max(version), 0) + 1 from quotation q where q.booking_id = b.id),
                    (select min(id) from price_list), 'VND', 0.30, ?, b.requested_pickup_date,
                    coalesce(d.departure_date, b.requested_pickup_date), coalesce(d.departure_date, b.requested_pickup_date), ?, 0, ?, ?, ?, ?
                from booking b left join departure d on d.id = b.departure_id where b.id = ? returning id
                """, Long.class, java.sql.Timestamp.from(expires), new BigDecimal(total),
                new BigDecimal(deposit), status.name(), draft ? null : fixture.managerId(),
                draft ? null : java.sql.Timestamp.from(START), booking.getId());
    }

    private long acceptTwice(Fixture fixture, long quote) {
        quotations.accept(quote, fixture.customer());
        quotations.accept(quote, fixture.customer());
        var existing = payments.payments(fixture.booking().getId(), fixture.customer());
        assertThat(existing).hasSize(1);
        return existing.getFirst().id();
    }

    private String checkout(long payment, Fixture fixture) {
        return UriComponentsBuilder.fromUriString(checkout.checkout(payment, fixture.customer(), "127.0.0.1").paymentUrl())
                .build().getQueryParams().getFirst("vnp_TxnRef");
    }

    private long reserveWideHorse(Fixture fixture) {
        var departure = data.departure("HCM-DN", LocalDate.of(2027, 1, 17), 4);
        jdbc.update("update booking set departure_id = ? where id = ?", departure.getId(), fixture.booking().getId());
        jdbc.update("update departure set booked_stall_count = 2 where id = ?", departure.getId());
        attachHorse(fixture, "WIDE");
        return departure.getId();
    }

    private long attachHorse(Fixture fixture, String stall) {
        Long horse = jdbc.queryForObject("""
                insert into horse (owner_customer_id, name, sex, horse_type_id, status)
                select ?, ?, 'MARE', min(id), 'ACTIVE' from horse_type returning id
                """, Long.class, fixture.booking().getCustomer().getUser().getId(), data.unique("a7-horse"));
        return jdbc.queryForObject("""
                insert into booking_horse (booking_id, horse_id, stall_type_code, status)
                values (?, ?, ?, 'ACTIVE') returning id
                """, Long.class, fixture.booking().getId(), horse, stall);
    }

    private void confirmWaiver(Fixture fixture, long horse) {
        long waiver = jdbc.queryForObject("""
                INSERT INTO terms_acceptance (booking_id, user_id, terms_type, terms_version)
                VALUES (?, ?, 'LIABILITY_WAIVER', 'test-a7') RETURNING id
                """, Long.class, fixture.booking().getId(), fixture.booking().getCustomer().getUser().getId());
        jdbc.update("""
                INSERT INTO horse_insurance (booking_horse_id, coverage_option, status, waiver_terms_acceptance_id,
                    confirmed_by, confirmed_at)
                VALUES (?, 'NONE', 'CONFIRMED', ?, ?, now())
                """, horse, waiver, fixture.managerId());
    }

    private void addAdjustmentLine(long quote, int number, String price) {
        jdbc.update("""
                insert into quotation_line (quotation_id, line_number, line_type, description, quantity, unit, unit_price, is_pass_through)
                values (?, ?, 'ADJUSTMENT', 'Điều chỉnh', 1, 'PER_BOOKING', ?, false)
                """, quote, number, new BigDecimal(price));
    }

    private long addServiceLine(long quote, int number, String service, String price) {
        return jdbc.queryForObject("""
                insert into quotation_line (quotation_id, line_number, line_type, billable_service_id, description,
                    quantity, unit, unit_price, is_pass_through)
                select ?, ?, 'SERVICE', id, name, 1, 'PER_HORSE', ?, is_pass_through
                from billable_service where code = ?
                returning id
                """, Long.class, quote, number, new BigDecimal(price), service);
    }

    private int refundRows(long booking) {
        return jdbc.queryForObject("select count(*) from payment where booking_id = ? and payment_type = 'REFUND'",
                Integer.class, booking);
    }

    private String bookingStatus(Fixture fixture) {
        return jdbc.queryForObject("select status from booking where id = ?", String.class, fixture.booking().getId());
    }

    private int bookedSeats(long departure) {
        return jdbc.queryForObject("select booked_stall_count from departure where id = ?", Integer.class, departure);
    }

    private int gatewayRows(long payment) {
        return jdbc.queryForObject("""
                select count(*) from payment_transaction where payment_id = ? and gateway_code = 'VNPAY'
                """, Integer.class, payment);
    }

    private String auth(String username, String role) {
        return "Bearer " + jwt.generateToken(username, List.of(role));
    }

    private static Map<String, String> callback(String reference, String transaction, int vnd, Instant paidAt, boolean success) {
        var params = new HashMap<String, String>();
        params.put("vnp_TxnRef", reference);
        params.put("vnp_TmnCode", "A7TEST");
        params.put("vnp_Amount", Integer.toString(vnd * 100));
        params.put("vnp_PayDate", GATEWAY_DATE.format(paidAt));
        params.put("vnp_ResponseCode", success ? "00" : "24");
        params.put("vnp_TransactionStatus", success ? "00" : "02");
        if (transaction != null) {
            params.put("vnp_TransactionNo", transaction);
        }
        params.put("vnp_SecureHash", VnpayUtil.sign(params, "a7-test-signature"));
        return params;
    }

    private static <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> runWhenReady(first, ready, go));
            var b = pool.submit(() -> runWhenReady(second, ready, go));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            return java.util.Arrays.asList(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        }
    }

    private static <T> T runWhenReady(Callable<T> task, CountDownLatch ready, CountDownLatch go) throws Exception {
        ready.countDown();
        if (!go.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Concurrent start timed out");
        }
        return task.call();
    }

    private record Fixture(Booking booking, String customer, String manager, Long managerId) {}
}
