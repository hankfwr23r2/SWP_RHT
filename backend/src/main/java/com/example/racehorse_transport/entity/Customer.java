package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "CUSTOMER")
public class Customer {
    @Id
    @Column(name = "UserID", nullable = false)
    private Integer id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @Size(max = 255)
    @Nationalized
    @Column(name = "FullName")
    private String fullName;

    @Size(max = 20)
    @Column(name = "Phone", length = 20)
    private String phone;

    @Size(max = 255)
    @Nationalized
    @Column(name = "Address")
    private String address;


}