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
public class RegulationDTO implements Serializable {
    private Integer id;
    private Integer staffID;
    private String regulationName;
    private String description;
    private String status;
}
