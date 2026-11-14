package com.example.racehorse_transport.dto.booking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

// Thông tin cho ăn khách khai cho từng ngựa lúc đặt chuyến; hộ tống đọc để cho ăn đúng giờ và đúng khẩu phần.
// Lưu trong JSON chi tiết đơn (không có cột riêng). Mọi trường đều không bắt buộc.
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HorseFeedingDto {
    private String usualTimes; // giờ ăn quen, vd "07:00, 12:00, 18:00"
    private String ration;     // khẩu phần và ghi chú, vd "8 kg cỏ/ngày, 2 kg yến mạch, kiêng đậu"
    private Boolean ownFeed;   // khách gửi kèm thức ăn riêng
}
