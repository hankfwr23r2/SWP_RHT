package com.example.racehorse_transport.util;

import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

// Ký và kiểm tra chữ ký HMAC-SHA512 theo tài liệu VNPAY v2.1.0
public final class VnpayUtil {

    public static final String SECURE_HASH = "vnp_SecureHash";
    public static final String SECURE_HASH_TYPE = "vnp_SecureHashType";

    private VnpayUtil() {
    }

    // Chuỗi tham số đã sắp xếp theo tên, bỏ giá trị rỗng, encode US-ASCII (dùng cho cả hash và query string)
    public static String buildQuery(Map<String, String> params) {
        StringJoiner joiner = new StringJoiner("&");
        new TreeMap<>(params).forEach((key, value) -> {
            if (value != null && !value.isEmpty()) {
                joiner.add(encode(key) + "=" + encode(value));
            }
        });
        return joiner.toString();
    }

    public static String sign(Map<String, String> params, String secret) {
        return hmacSha512(secret, buildQuery(params));
    }

    public static String buildPaymentUrl(String payUrl, Map<String, String> params, String secret) {
        String query = buildQuery(params);
        return payUrl + "?" + query + "&" + SECURE_HASH + "=" + hmacSha512(secret, query);
    }

    // Kiểm tra chữ ký của tham số VNPAY gửi về (IPN hoặc Return URL)
    public static boolean verify(Map<String, String> params, String secret) {
        String received = params.get(SECURE_HASH);
        if (received == null || received.isBlank() || secret == null || secret.isBlank()) {
            return false;
        }
        Map<String, String> fields = new HashMap<>(params);
        fields.remove(SECURE_HASH);
        fields.remove(SECURE_HASH_TYPE);
        String expected = sign(fields, secret);
        return MessageDigest.isEqual(
                expected.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                received.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    public static String hmacSha512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Không tạo được chữ ký VNPAY", ex);
        }
    }

    // IP thật của khách khi backend chạy sau proxy (Render, Nginx...)
    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String ip = request.getRemoteAddr();
        return (ip == null || ip.isBlank()) ? "127.0.0.1" : ip;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }
}
