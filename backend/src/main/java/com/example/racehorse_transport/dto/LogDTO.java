package com.example.racehorse_transport.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LogDTO implements Serializable {
    private Integer id;
    private Integer bookingID;
    private Integer staffID;
    private String logType;
    private String status;
    private String note;
    private Instant createdAt;
}
