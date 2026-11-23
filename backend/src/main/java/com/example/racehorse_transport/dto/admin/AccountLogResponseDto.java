package com.example.racehorse_transport.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AccountLogResponse")
public class AccountLogResponseDto {
    private Integer id;
    private String actionType;
    private String actionByName;
    private String actionByEmail;
    private Integer targetUserId;
    private String oldData;
    private String newData;
    private String description;
    private Instant createdAt;
}
