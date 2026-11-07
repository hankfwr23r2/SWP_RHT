package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.scheduler.QuotationExpiryScheduler;
import com.example.racehorse_transport.service.BookingService;
import com.example.racehorse_transport.service.VnpayService;
import com.example.racehorse_transport.util.VnpayUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Hai transaction thật trên PostgreSQL, buộc một bên chờ khóa trong lúc bên kia chưa commit. */
@SpringBootTest(properties = {"vnpay.hash-secret=DEADLINE_TEST_SECRET", "spring.datasource.hikari.maximum-pool-size=5"})
class DepositPaymentConcurrencyTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final Instant DEADLINE = Instant.parse("2026-11-07T03:00:00Z");
    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired BookingService bookingService;
    @Autowired VnpayService vnpayService;
    @Autowired BookingRepository bookings;
    @Autowired PaymentRepository payments;
    @Autowired QuotationRepository quotations;
    @Autowired BookingVehicleRepository bookingVehicles;
    @Autowired BookingAssignmentRepository assignments;
    @Autowired VehicleRepository vehicles;
    @Autowired StaffRepository staff;
    @Autowired UserRepository users;
    @Autowired CustomerRepository customers;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean Clock clock;
    @MockitoBean QuotationExpiryScheduler scheduler;

    private TransactionTemplate transactions;
    private Integer bookingId;
    private Integer paymentId;
    private String txnRef;

    @BeforeEach
    void pendingDepositWithAssignedFleet() {
        transactions = new TransactionTemplate(transactionManager);
        when(clock.instant()).thenReturn(DEADLINE.plusSeconds(299));
        transactions.executeWithoutResult(status -> {
            User owner = new User();
            owner.setUsername("deposit-" + UUID.randomUUID());
            owner = users.save(owner);
            Customer customer = new Customer();
            customer.setUser(owner);
            customer = customers.save(customer);
            Booking booking = bookings.save(Booking.builder().customerID(customer).bookingType("domestic")
                    .bookingDate(DEADLINE.minusSeconds(3600)).departureDate(DEADLINE.plusSeconds(86400))
                    .status("awaiting_payment").detailsJson("{\"horses\":[],\"history\":[]}").build());
            bookingId = booking.getId();
            quotations.save(Quotation.builder().booking(booking).status("PENDING")
                    .sentAt(DEADLINE.minusSeconds(3600)).expiresAt(DEADLINE)
                    .subtotal(new BigDecimal("10000000")).adjustmentAmount(BigDecimal.ZERO)
                    .totalAmount(new BigDecimal("10000000")).balanceAmount(new BigDecimal("7000000"))
                    .depositAmount(new BigDecimal("3000000")).build());
            txnRef = "DEP-" + UUID.randomUUID();
            paymentId = payments.save(Payment.builder().bookingID(booking).amount(new BigDecimal("3000000"))
                    .paymentType("DEPOSIT").paymentMethod("VNPAY").status("PENDING")
                    .paymentDate(DEADLINE.minusSeconds(60)).transactionCode(txnRef).build()).getId();
            Vehicle vehicle = vehicles.save(Vehicle.builder().licensePlate("TEST-" + bookingId).status("AVAILABLE").build());
            bookingVehicles.save(BookingVehicle.builder().id(new BookingVehicleId(bookingId, vehicle.getId()))
                    .bookingID(booking).vehicleID(vehicle).build());
            for (String role : new String[]{"DRIVER", "ESCORT"}) {
                User user = new User();
                user.setUsername(role + UUID.randomUUID());
                user = users.save(user);
                Staff member = new Staff();
                member.setUser(user);
                member.setRole(role);
                member = staff.save(member);
                assignments.save(BookingAssignment.builder().bookingID(booking).staffID(member)
                        .assignRole(role).status("ASSIGNED").build());
            }
        });
    }

    private Map<String, String> success() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_Amount", "300000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "123456789");
        params.put("vnp_PayDate", "20261107095959"); // GMT+7: một giây trước hạn.
        params.put(VnpayUtil.SECURE_HASH, VnpayUtil.sign(params, "DEADLINE_TEST_SECRET"));
        return params;
    }

    private void holdCommit(CountDownLatch processed, CountDownLatch commit) {
        processed.countDown();
        try {
            assertThat(commit.await(15, TimeUnit.SECONDS)).as("cho phép transaction commit").isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private void awaitDatabaseLock() throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < until) {
            Integer waiting = jdbc.queryForObject("select count(*) from pg_stat_activity "
                    + "where datname = current_database() and wait_event_type = 'Lock'", Integer.class);
            if (waiting != null && waiting > 0) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Luồng còn lại phải chờ khóa PostgreSQL trước khi được xử lý đơn");
    }

    @Test
    void confirmationWins_expiryMustWaitThenKeepPaidBookingAndAssignments() throws Exception {
        CountDownLatch processed = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var confirmed = executor.submit(() -> transactions.execute(status -> {
                var result = vnpayService.confirm(success());
                holdCommit(processed, commit);
                return result;
            }));
            try {
                assertThat(processed.await(10, TimeUnit.SECONDS)).isTrue();
                when(clock.instant()).thenReturn(DEADLINE.plusSeconds(300));
                var expired = executor.submit(() -> bookingService.releaseExpiredQuotations());
                awaitDatabaseLock();
                commit.countDown();
                assertThat(confirmed.get(15, TimeUnit.SECONDS).isSuccess()).isTrue();
                assertThat(expired.get(15, TimeUnit.SECONDS)).isZero();
            } finally {
                commit.countDown();
            }
        }
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo("waybill_issued");
        assertThat(payments.findById(paymentId).orElseThrow().getStatus()).isEqualTo("COMPLETED");
        assertThat(bookingVehicles.findByBookingID_Id(bookingId)).hasSize(1);
        assertThat(assignments.findByBookingID_Id(bookingId)).hasSize(2);
    }

    @Test
    void expiryWins_lateConfirmationMustWaitThenRecordMoneyForReviewWithoutRestoringFleet() throws Exception {
        when(clock.instant()).thenReturn(DEADLINE.plusSeconds(300));
        CountDownLatch processed = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var expired = executor.submit(() -> transactions.execute(status -> {
                int released = bookingService.releaseExpiredQuotations();
                holdCommit(processed, commit);
                return released;
            }));
            try {
                assertThat(processed.await(10, TimeUnit.SECONDS)).isTrue();
                var confirmed = executor.submit(() -> vnpayService.confirm(success()));
                awaitDatabaseLock();
                commit.countDown();
                assertThat(expired.get(15, TimeUnit.SECONDS)).isEqualTo(1);
                assertThat(confirmed.get(15, TimeUnit.SECONDS).getPaymentStatus()).isEqualTo("NEEDS_REVIEW");
            } finally {
                commit.countDown();
            }
        }
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo("quote_expired");
        assertThat(payments.findById(paymentId).orElseThrow().getStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(bookingVehicles.findByBookingID_Id(bookingId)).isEmpty();
        assertThat(assignments.findByBookingID_Id(bookingId)).isEmpty();
    }
}
