package com.example.racehorse_transport.dto.field;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "DeliveryTimeoutRequest")
public class DeliveryTimeoutRequestDto {

    private String driverUsername;

    private String tripId; // chuyến đang ở điểm giao

    private boolean confirmEarly; // báo trước khi chờ đủ 2 giờ: tài xế đã xác nhận (demo không bắt chờ)

    private String timeoutReason; // UNREACHABLE (Không liên lạc được), DESTINATION_UNREADY (Trại đích chưa nhận), CONSIGNEE_REJECTED (Từ chối nhận)

    private String selectedAction; // STABLE_NEARBY (Thuê chuồng lưu trú khẩn cấp), RETURN_TO_HQ (Chở ngựa quay về Trụ sở chính VN)

    private String partnerName; // Tên chuồng / trang trại gửi ngựa

    private String partnerAddress; // Địa chỉ chuồng để người nhận tới lấy ngựa

    private String partnerPhone; // Số điện thoại chuồng gửi cho khách liên hệ

    private String photoUrl; // Ảnh bằng chứng ngựa đã gửi tại chuồng (tiền chuồng người nhận tự trả khi lấy ngựa)

    private String note;
}
