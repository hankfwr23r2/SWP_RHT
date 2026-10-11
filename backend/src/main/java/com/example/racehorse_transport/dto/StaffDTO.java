package com.example.racehorse_transport.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StaffDTO implements Serializable {
    private Integer id;
    private Integer user;
    private String role;
    private String staffCode;
    private LocalDate hireDate;
    private String employmentStatus;
}
