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
public class SystemLogDTO implements Serializable {
    private Integer id;
    private Integer actionbyUserid;
    private String actionType;
    private String targetTable;
    private Integer targetRecordID;
    private String oldData;
    private String newData;
    private Instant createdAt;
}
