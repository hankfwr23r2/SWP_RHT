package com.example.racehorse_transport.dto.settlement;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingDto {
    @Min(1)
    @Max(5)
    private Integer stars; // 1 đến 5 sao

    private String driverFeedback; // Nhận xét về độ êm ái, an toàn của tài xế
    private String escortFeedback; // Nhận xét về sự chăm sóc sức khỏe của Escort
    private String overallFeedback; // Đánh giá chung
}
