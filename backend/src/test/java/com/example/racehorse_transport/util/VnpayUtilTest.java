package com.example.racehorse_transport.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VnpayUtilTest {

    private static final String SECRET = "TESTSECRET";

    private Map<String, String> sampleParams() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_TxnRef", "DEP00121");
        params.put("vnp_OrderInfo", "Thanh toan coc 30% don BK-12");
        params.put("vnp_Amount", "300000000");
        params.put("vnp_BankCode", "");
        return params;
    }

    @Test
    void buildQuery_sortsKeysSkipsEmptyAndEncodes() {
        assertThat(VnpayUtil.buildQuery(sampleParams())).isEqualTo(
                "vnp_Amount=300000000&vnp_OrderInfo=Thanh+toan+coc+30%25+don+BK-12&vnp_TxnRef=DEP00121&vnp_Version=2.1.0");
    }

    @Test
    void sign_matchesIndependentHmacSha512() {
        // Giá trị tính độc lập bằng Python hmac.new(secret, query, sha512)
        assertThat(VnpayUtil.sign(sampleParams(), SECRET)).isEqualTo(
                "32a83d97db683d3c90880bce1a90e8fa2c46a4d2fe291c8155f692c95a08befc4f966ebe8f6874a3efcdadbef22a5a44081aeb68d801c19ec80729b2d5539daf");
    }

    @Test
    void buildPaymentUrl_appendsSecureHash() {
        String url = VnpayUtil.buildPaymentUrl("https://pay.test/vpcpay.html", sampleParams(), SECRET);
        assertThat(url).startsWith("https://pay.test/vpcpay.html?vnp_Amount=300000000&")
                .endsWith("&vnp_SecureHash=" + VnpayUtil.sign(sampleParams(), SECRET));
    }

    @Test
    void verify_acceptsUntamperedParams() {
        Map<String, String> params = sampleParams();
        params.put(VnpayUtil.SECURE_HASH, VnpayUtil.sign(params, SECRET).toUpperCase());
        params.put(VnpayUtil.SECURE_HASH_TYPE, "HmacSHA512");
        assertThat(VnpayUtil.verify(params, SECRET)).isTrue();
    }

    @Test
    void verify_rejectsTamperedAmountWrongSecretOrMissingHash() {
        Map<String, String> params = sampleParams();
        params.put(VnpayUtil.SECURE_HASH, VnpayUtil.sign(params, SECRET));

        assertThat(VnpayUtil.verify(params, "OTHERSECRET")).isFalse();
        assertThat(VnpayUtil.verify(params, "")).isFalse();

        Map<String, String> tampered = new HashMap<>(params);
        tampered.put("vnp_Amount", "100");
        assertThat(VnpayUtil.verify(tampered, SECRET)).isFalse();

        Map<String, String> missing = new HashMap<>(params);
        missing.remove(VnpayUtil.SECURE_HASH);
        assertThat(VnpayUtil.verify(missing, SECRET)).isFalse();
    }
}
