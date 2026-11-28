package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.support.TestData;
import com.example.racehorse_transport.util.OffsetClock;
import com.example.racehorse_transport.util.VnpayUtil;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.util.UriComponentsBuilder;

// Tua giờ chạy thử mà vẫn trả qua VNPAY thật: cổng chạy theo giờ thật, hệ thống chạy theo giờ đã tua.
@PostgresIntegrationTest
@Import(VnpayTimeTravelTest.TravelClock.class)
@TestPropertySource(properties = {
        "vnpay.tmn-code=TRAVELTEST",
        "vnpay.hash-secret=travel-test-signature",
        "vnpay.quotation-expiry-scan-ms=3600000"
})
class VnpayTimeTravelTest {

    private static final Duration TRAVELLED = Duration.ofDays(3);
    private static final ZoneId GATEWAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter GATEWAY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @TestConfiguration(proxyBeanMethods = false)
    static class TravelClock {
        @Bean
        @Primary
        OffsetClock travelClock() {
            return new OffsetClock(Clock.systemUTC(), TRAVELLED);
        }
    }

    @Autowired private QuotationService quotations;
    @Autowired private PaymentService payments;
    @Autowired private PaymentCheckoutService checkout;
    @Autowired private PaymentResultService results;
    @Autowired private TestData data;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private OffsetClock clock;

    @BeforeEach
    void travel() {
        clock.setOffset(TRAVELLED);
    }

    @Test
    void paymentLinkSendsRealTimeToTheGateway() {
        var link = checkoutDeposit();

        assertThat(gatewayInstant(link.query().get("vnp_CreateDate")))
                .isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
        assertThat(gatewayInstant(link.query().get("vnp_ExpireDate")))
                .isCloseTo(Instant.now().plus(15, ChronoUnit.MINUTES), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void depositPaidInRealTimeIsRecordedOnTheTravelledClock() {
        var link = checkoutDeposit();

        var shown = results.receiveReturn(paidNow(link));

        assertThat(shown.success()).isTrue();
        assertThat(jdbc.queryForObject("select status from booking where id = ?", String.class, link.bookingId()))
                .isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select paid_at from payment where id = ?", Timestamp.class, link.paymentId())
                .toInstant()).isCloseTo(Instant.now().plus(TRAVELLED), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void reloadingTheResultAfterTravellingFurtherStillShowsSuccess() {
        var link = checkoutDeposit();
        var callback = paidNow(link);
        assertThat(results.receiveReturn(callback).success()).isTrue();

        clock.setOffset(TRAVELLED.plusDays(1));
        var reloaded = results.receiveReturn(callback);

        assertThat(reloaded.success()).isTrue();
        assertThat(reloaded.needsReview()).isFalse();
    }

    private Link checkoutDeposit() {
        var customer = data.customer(data.unique("travel-customer"));
        var manager = data.staff(data.unique("travel-manager"), "MANAGER");
        var booking = data.booking(customer, BookingType.LOCAL, BookingStatus.AWAITING_DEPOSIT);
        Instant now = clock.instant();
        long quote = jdbc.queryForObject("""
                insert into quotation (booking_id, version, price_list_id, currency_code, deposit_rate, expires_at,
                    confirmed_pickup_date, confirmed_departure_date, estimated_delivery_date, total_amount, pass_through_amount,
                    deposit_amount, status, sent_by, sent_at)
                select id, 1, (select min(id) from price_list), 'VND', 0.30, ?, requested_pickup_date,
                    requested_pickup_date, requested_pickup_date, 10000, 0, 3000, 'SENT', ?, ?
                from booking where id = ? returning id
                """, Long.class, Timestamp.from(now.plus(2, ChronoUnit.HOURS)), manager.getUserId(),
                Timestamp.from(now), booking.getId());
        String username = customer.getUser().getUsername();
        quotations.accept(quote, username);
        long payment = payments.payments(booking.getId(), username).getFirst().id();
        String url = checkout.checkout(payment, username, "127.0.0.1").paymentUrl();
        Map<String, String> query = new HashMap<>();
        UriComponentsBuilder.fromUriString(url).build().getQueryParams()
                .forEach((name, values) -> query.put(name, URLDecoder.decode(values.getFirst(), StandardCharsets.UTF_8)));
        return new Link(booking.getId(), payment, query);
    }

    // Kết quả như cổng thật gửi về: vnp_PayDate là giờ thật
    private static Map<String, String> paidNow(Link link) {
        var params = new HashMap<String, String>();
        params.put("vnp_TxnRef", link.query().get("vnp_TxnRef"));
        params.put("vnp_TmnCode", "TRAVELTEST");
        params.put("vnp_Amount", link.query().get("vnp_Amount"));
        params.put("vnp_PayDate", GATEWAY_TIME.format(Instant.now().atZone(GATEWAY_ZONE)));
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", String.valueOf(System.nanoTime()));
        params.put(VnpayUtil.SECURE_HASH, VnpayUtil.sign(params, "travel-test-signature"));
        return params;
    }

    private static Instant gatewayInstant(String value) {
        return LocalDateTime.parse(value, GATEWAY_TIME).atZone(GATEWAY_ZONE).toInstant();
    }

    private record Link(long bookingId, long paymentId, Map<String, String> query) {}
}
