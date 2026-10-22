package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.payment.VnpayCreateRequest;
import com.example.racehorse_transport.dto.payment.VnpayCreateResponse;
import com.example.racehorse_transport.dto.payment.VnpayResultResponse;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.VnpayService;
import com.example.racehorse_transport.util.VnpayUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/payments/vnpay")
@RequiredArgsConstructor
public class VnpayController {

    private final VnpayService vnpayService;
    private final JwtUtils jwtUtils;

    private String extractUsername(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtils.validateToken(token)) {
                return jwtUtils.getUsernameFromToken(token);
            }
        }
        return null;
    }

    /**
     * 1. Khách hàng tạo link thanh toán VNPAY (cọc 30% / số dư 70%)
     * POST /api/v1/payments/vnpay/create
     */
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<VnpayCreateResponse>> create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @Valid @RequestBody VnpayCreateRequest request,
            HttpServletRequest httpRequest) {
        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Vui lòng đăng nhập để thanh toán!"));
        }
        if (request.getReturnUrl() == null || request.getReturnUrl().isBlank()) {
            String origin = httpRequest.getHeader("Origin");
            if (origin != null && !origin.isBlank() && origin.startsWith("http")) {
                request.setReturnUrl(origin.replaceAll("/+$", "") + "/payment/vnpay-return");
            }
        }
        VnpayCreateResponse response = vnpayService.createPaymentUrl(request, username, VnpayUtil.clientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success(response, "Đã tạo link thanh toán VNPAY"));
    }

    /**
     * 2. VNPAY gọi server-to-server báo kết quả (IPN). Không cần đăng nhập, bảo vệ bằng chữ ký.
     * GET /api/v1/payments/vnpay/ipn — phản hồi đúng định dạng {RspCode, Message} theo tài liệu VNPAY
     */
    @GetMapping("/ipn")
    public ResponseEntity<Map<String, String>> ipn(@RequestParam Map<String, String> params) {
        String rspCode;
        try {
            rspCode = vnpayService.confirm(params).getRspCode();
        } catch (Exception ex) {
            log.error("VNPAY IPN error, txnRef={}: {}", params.get("vnp_TxnRef"), ex.getMessage(), ex);
            rspCode = "99";
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("RspCode", rspCode);
        body.put("Message", switch (rspCode) {
            case "00" -> "Confirm Success";
            case "01" -> "Order not found";
            case "02" -> "Order already confirmed";
            case "04" -> "Invalid amount";
            case "97" -> "Invalid signature";
            default -> "Unknown error";
        });
        return ResponseEntity.ok(body);
    }

    /**
     * 3. Trang kết quả Frontend gửi lại tham số VNPAY trả về trình duyệt để kiểm tra và hiển thị.
     * Dùng chung logic xác nhận với IPN (idempotent) để chạy được cả khi IPN không tới (localhost).
     * GET /api/v1/payments/vnpay/return
     */
    @GetMapping("/return")
    public ResponseEntity<ApiResponse<VnpayResultResponse>> vnpayReturn(@RequestParam Map<String, String> params) {
        try {
            VnpayResultResponse response = vnpayService.confirm(params);
            return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));
        } catch (Exception ex) {
            log.error("VNPAY Return confirmation error, txnRef={}: {}", params.get("vnp_TxnRef"), ex.getMessage(), ex);
            VnpayResultResponse fallback = VnpayResultResponse.builder()
                    .success(false)
                    .rspCode("99")
                    .message("Không thể hoàn tất kiểm tra giao dịch: " + ex.getMessage())
                    .transactionCode(params.get("vnp_TxnRef"))
                    .build();
            return ResponseEntity.ok(ApiResponse.success(fallback, fallback.getMessage()));
        }
    }
}
