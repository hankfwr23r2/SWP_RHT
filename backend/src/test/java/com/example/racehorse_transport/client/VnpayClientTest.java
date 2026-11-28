package com.example.racehorse_transport.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.config.VnpayProperties;
import com.example.racehorse_transport.util.VnpayUtil;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VnpayClientTest {

    // Khóa giả, chỉ dùng trong test
    private static final String FAKE_SECRET = "fake-secret-for-test";
    private static final Instant CREATED = Instant.parse("2026-12-01T03:00:00Z");

    private static VnpayClient client(String secret) {
        VnpayProperties properties = new VnpayProperties();
        ReflectionTestUtils.setField(properties, "tmnCode", "TESTTMN");
        ReflectionTestUtils.setField(properties, "hashSecret", secret);
        ReflectionTestUtils.setField(properties, "payUrl", "https://gateway.test/pay");
        ReflectionTestUtils.setField(properties, "returnUrl", "https://app.test/return");
        ReflectionTestUtils.setField(properties, "expireMinutes", 15);
        return new VnpayClient(properties, Clock.systemUTC());
    }

    private static Map<String, String> query(String url) {
        Map<String, String> params = new HashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            params.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return params;
    }

    private static Map<String, String> callback() {
        Map<String, String> params = new HashMap<>(Map.of(
                "vnp_TxnRef", "DP-1", "vnp_Amount", "370000000", "vnp_ResponseCode", "00", "vnp_TransactionStatus", "00",
                "vnp_TransactionNo", "14000001", "vnp_BankCode", "NCB", "vnp_PayDate", "20261201100500"));
        params.put(VnpayUtil.SECURE_HASH, VnpayUtil.sign(params, FAKE_SECRET));
        return params;
    }

    @Test
    void paymentUrlIsSignedAndUsesConfiguredReturnPage() {
        VnpayClient client = client(FAKE_SECRET);
        String url = client.createPaymentUrl("DP-1", new BigDecimal("3700000.00"), "Dat coc BK-1", "127.0.0.1", CREATED,
                CREATED.plusSeconds(900));
        Map<String, String> params = query(url);

        assertThat(url).startsWith("https://gateway.test/pay?");
        assertThat(params).containsEntry("vnp_Amount", "370000000").containsEntry("vnp_TxnRef", "DP-1")
                .containsEntry("vnp_ReturnUrl", "https://app.test/return")
                .containsEntry("vnp_CreateDate", "20261201100000").containsEntry("vnp_ExpireDate", "20261201101500");
        assertThat(client.hasValidSignature(params)).isTrue();
    }

    @Test
    void readsVerifiedResult() {
        VnpayClient client = client(FAKE_SECRET);
        assertThat(client.hasValidSignature(callback())).isTrue();

        var result = client.read(callback());
        assertThat(result.reference()).isEqualTo("DP-1");
        assertThat(result.transactionNumber()).isEqualTo("14000001");
        assertThat(result.amount()).isEqualByComparingTo("3700000");
        assertThat(result.success()).isTrue();
        assertThat(result.paidAt()).isEqualTo(Instant.parse("2026-12-01T03:05:00Z"));
    }

    @Test
    void rejectsTamperedOrUnsignedCallback() {
        VnpayClient client = client(FAKE_SECRET);
        Map<String, String> tampered = callback();
        tampered.put("vnp_Amount", "100");
        assertThat(client.hasValidSignature(tampered)).isFalse();
        Map<String, String> unsigned = callback();
        unsigned.remove(VnpayUtil.SECURE_HASH);
        assertThat(client.hasValidSignature(unsigned)).isFalse();
        assertThat(client("").hasValidSignature(callback())).isFalse();
    }

    @Test
    void successNeedsBothCodesAndBadValuesBecomeNull() {
        VnpayClient client = client(FAKE_SECRET);
        Map<String, String> params = callback();
        params.put("vnp_TransactionStatus", "02");
        params.put("vnp_PayDate", "khong-phai-ngay");
        params.put("vnp_Amount", "-5");
        var result = client.read(params);
        assertThat(result.success()).isFalse();
        assertThat(result.paidAt()).isNull();
        assertThat(result.amount()).isNull();
    }

    @Test
    void refusesMissingConfigurationAndFractionalAmount() {
        assertThatThrownBy(() -> client("").createPaymentUrl("DP-1", BigDecimal.TEN, "x", "127.0.0.1", CREATED, CREATED))
                .isInstanceOf(IllegalStateException.class);
        for (String amount : new String[] {"0", "-1", "10.50"}) {
            assertThatThrownBy(() -> client(FAKE_SECRET).createPaymentUrl("DP-1", new BigDecimal(amount), "x", "127.0.0.1",
                    CREATED, CREATED)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
