package com.example.racehorse_transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

// Khóa của hub_service_area.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class HubServiceAreaId implements Serializable {

    @Column(name = "hub_location_id", nullable = false)
    private Long hubLocationId;

    @Column(name = "province_code", length = 10, nullable = false)
    private String provinceCode;
}
