package com.example.racehorse_transport.dto.field;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryTimeoutRequest {

    private String driverUsername;

    private String timeoutReason; // UNREACHABLE (Không liên lạc được), DESTINATION_UNREADY (Trại đích chưa nhận), CONSIGNEE_REJECTED (Từ chối nhận)

    private String selectedAction; // STABLE_NEARBY (Thuê chuồng lưu trú khẩn cấp), RETURN_TO_HQ (Chở ngựa quay về Trụ sở chính VN)

    private String partnerName; // Tên trang trại / trạm thú y lưu trú tạm thời

    private String partnerPhone;

    private BigDecimal estimatedCost; // Phí thuê chuồng trọ ngoài + phí lưu ca xe

    private String note;
}
