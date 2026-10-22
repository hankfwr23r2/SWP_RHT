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

    // Chuỗi tham số đã sắp xếp theo tên, bỏ giá trị rỗng, encode UTF-8 (dùng cho cả hash và query string khi tạo URL thanh toán)
    public static String buildQuery(Map<String, String> params) {
        StringJoiner joiner = new StringJoiner("&");
        new TreeMap<>(params).forEach((key, value) -> {
            if (value != null && !value.isEmpty()) {
                joiner.add(encode(key) + "=" + encode(value));
            }
        });
        return joiner.toString();
    }

    // Chuỗi tham số đã sắp xếp theo tên, bỏ giá trị rỗng, không URL-encode (chuẩn VNPAY dùng để verify Return URL & IPN)
    public static String buildRawQuery(Map<String, String> params) {
        StringJoiner joiner = new StringJoiner("&");
        new TreeMap<>(params).forEach((key, value) -> {
            if (value != null && !value.isEmpty()) {
                joiner.add(key + "=" + value);
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
    // Hỗ trợ cả chuẩn raw text theo tài liệu SDK VNPay lẫn chuẩn query-encoded để đảm bảo tương thích 100%
    public static boolean verify(Map<String, String> params, String secret) {
        String received = params.get(SECURE_HASH);
        if (received == null || received.isBlank() || secret == null || secret.isBlank()) {
            return false;
        }
        Map<String, String> fields = new HashMap<>(params);
        fields.remove(SECURE_HASH);
        fields.remove(SECURE_HASH_TYPE);

        // 1. Kiểm tra theo chuẩn chuỗi thô (Raw) mà framework/Spring Boot đã parse
        String rawHash = hmacSha512(secret, buildRawQuery(fields));
        if (MessageDigest.isEqual(
                rawHash.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                received.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            return true;
        }

        // 2. Dự phòng trường hợp đối tác gửi/tính trên chuỗi đã URL-encode
        String encodedHash = sign(fields, secret);
        return MessageDigest.isEqual(
                encodedHash.toLowerCase().getBytes(StandardCharsets.US_ASCII),
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

    // IP thật của khách khi backend chạy sau proxy (Render, Nginx...). VNPay chỉ chấp nhận IPv4 hợp lệ.
    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String ip = forwarded.split(",")[0].trim();
            if (isValidIpv4(ip)) {
                return ip;
            }
        }
        String ip = request.getRemoteAddr();
        if (ip != null && isValidIpv4(ip.trim())) {
            return ip.trim();
        }
        return "127.0.0.1";
    }

    private static boolean isValidIpv4(String ip) {
        return ip != null && ip.matches("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.){3}(25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)$");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
