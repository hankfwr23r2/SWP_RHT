package com.example.racehorse_transport.client;

import com.example.racehorse_transport.config.VnpayProperties;
import com.example.racehorse_transport.util.OffsetClock;
import com.example.racehorse_transport.util.VnpayUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Giao tiếp với cổng VNPAY (Payment API v2.1.0): tạo link và đọc kết quả gửi về. Không quyết định trạng thái đơn hay khoản thu.
@Component
@RequiredArgsConstructor
public class VnpayClient {

    public static final String GATEWAY_CODE = "VNPAY";

    private static final ZoneId GATEWAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter GATEWAY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String SUCCESS = "00";

    private final VnpayProperties properties;
    private final Clock clock;

    // Kết quả đã kiểm chữ ký. paidAt null khi cổng không gửi hoặc gửi sai định dạng.
    public record GatewayResult(String reference, String transactionNumber, String bankCode, String responseCode,
                                BigDecimal amount, boolean success, Instant paidAt) {
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public int defaultExpireMinutes() {
        return properties.getExpireMinutes();
    }

    public int depositConfirmationGraceMinutes() {
        return properties.getDepositConfirmationGraceMinutes();
    }

    // Trang kết quả lấy từ cấu hình, không nhận từ client
    public String createPaymentUrl(String reference, BigDecimal amount, String orderInfo, String clientIp,
                                   Instant createdAt, Instant expiresAt) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("Hệ thống chưa cấu hình VNPAY.");
        }
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", VnpayProperties.VERSION);
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", properties.getTmnCode());
        params.put("vnp_Amount", gatewayAmount(amount));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", reference);
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", properties.getReturnUrl());
        params.put("vnp_IpAddr", clientIp);
        params.put("vnp_CreateDate", gatewayTime(createdAt.minus(travelled())));
        params.put("vnp_ExpireDate", gatewayTime(expiresAt.minus(travelled())));
        return VnpayUtil.buildPaymentUrl(properties.getPayUrl(), params, properties.getHashSecret());
    }

    public boolean hasValidSignature(Map<String, String> params) {
        return VnpayUtil.verify(params, properties.getHashSecret());
    }

    // Chỉ gọi sau khi hasValidSignature trả về true
    public GatewayResult read(Map<String, String> params) {
        String responseCode = params.get("vnp_ResponseCode");
        return new GatewayResult(params.get("vnp_TxnRef"), params.get("vnp_TransactionNo"), params.get("vnp_BankCode"),
                responseCode, amount(params.get("vnp_Amount")),
                SUCCESS.equals(responseCode) && SUCCESS.equals(params.get("vnp_TransactionStatus")),
                paidAt(params.get("vnp_PayDate")));
    }

    // VNPAY chỉ nhận số nguyên đồng (nhân 100), nên tiền lẻ phải được làm tròn trước khi tạo khoản thu
    public static boolean isPayable(BigDecimal amount) {
        return amount != null && amount.signum() > 0 && amount.stripTrailingZeros().scale() <= 0;
    }

    // Cổng chạy theo giờ thật; hệ thống đang tua giờ chạy thử thì phải đổi qua lại. Bình thường bằng 0.
    private Duration travelled() {
        return clock instanceof OffsetClock travel ? travel.offset() : Duration.ZERO;
    }

    private static String gatewayAmount(BigDecimal amount) {
        if (!isPayable(amount)) {
            throw new IllegalArgumentException("Số tiền thanh toán VNPAY phải là số nguyên đồng lớn hơn 0.");
        }
        return amount.multiply(HUNDRED).toBigIntegerExact().toString();
    }

    private static BigDecimal amount(String value) {
        if (value == null || !value.matches("\\d{1,17}")) {
            return null;
        }
        return new BigDecimal(value).divide(HUNDRED, 2, RoundingMode.UNNECESSARY);
    }

    private static String gatewayTime(Instant instant) {
        return GATEWAY_TIME.format(instant.atZone(GATEWAY_ZONE));
    }

    private Instant paidAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, GATEWAY_TIME).atZone(GATEWAY_ZONE).toInstant().plus(travelled());
        } catch (DateTimeException e) {
            return null;
        }
    }
}
