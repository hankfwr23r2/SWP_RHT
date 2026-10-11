package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "STAFF")
public class Staff {
    @Id
    @Column(name = "UserID", nullable = false)
    private Integer id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @Size(max = 100)
    @Column(name = "Role", length = 100)
    private String role;

    @Size(max = 50)
    @Column(name = "StaffCode", length = 50)
    private String staffCode;

    @Column(name = "HireDate")
    private LocalDate hireDate;

    @Size(max = 50)
    @Column(name = "EmploymentStatus", length = 50)
    private String employmentStatus;


}