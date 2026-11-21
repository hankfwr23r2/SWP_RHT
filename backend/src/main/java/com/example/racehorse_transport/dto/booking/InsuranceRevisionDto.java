package com.example.racehorse_transport.dto.booking;

import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InsuranceRevisionDto {
    private String action; // supplemented: bổ sung trên loại cũ; switched: không bổ sung được, đổi loại
    private String fromOption;
    private String toOption;
    private String requestReason;
    private String customerReason;
    private Long at;
}
