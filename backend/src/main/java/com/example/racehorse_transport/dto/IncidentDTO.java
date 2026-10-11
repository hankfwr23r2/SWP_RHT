package com.example.racehorse_transport.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IncidentDTO implements Serializable {
    private Integer id;
    private Integer bookingID;
    private Integer staffID;
    private Integer logID;
    private String incidentName;
    private String incidentType;
    private String description;
    private String status;
}
