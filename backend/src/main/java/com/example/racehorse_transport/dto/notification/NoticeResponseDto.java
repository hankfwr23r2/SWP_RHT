package com.example.racehorse_transport.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "NoticeResponse")
public class NoticeResponseDto {
    private String id;
    private String role;
    private String name;
    private String title;
    private String text;
    private String link;
    private String bookingId;
    private Long at;
    private Boolean read;
}
