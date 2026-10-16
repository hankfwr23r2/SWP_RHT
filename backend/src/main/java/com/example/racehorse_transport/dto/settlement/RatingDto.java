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
    private Integer stars; // Đánh giá chung toàn chuyến 1 đến 5 sao

    private String comment; // Nhận xét, góp ý của khách hàng
    private Long submittedAt; // Thời gian gửi đánh giá

    private String driverFeedback; // Nhận xét về tài xế (backward compat)
    private String escortFeedback; // Nhận xét về Escort (backward compat)
    private String overallFeedback; // Đánh giá chung (backward compat)
}
