package com.example.racehorse_transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Loại khoang: STANDARD, WIDE.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stall_type")
public class StallType {

    @Id
    @Column(name = "code", length = 30, nullable = false)
    private String code;

    @Column(name = "name", length = 200, nullable = false)
    private String name;
}
