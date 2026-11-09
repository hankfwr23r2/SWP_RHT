package com.example.racehorse_transport.dto.manager;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendBackRequest {
    private String by;
    private String to; // "specialist", "coordinator", or "both"
    private String reason;
    private String specialistReason; // lý do riêng gửi Chuyên viên
    private String coordinatorReason; // lý do riêng gửi Điều phối viên
}
