package com.example.racehorse_transport.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Tỉnh nào do hub nào phục vụ. Mỗi tỉnh thuộc đúng một hub.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "hub_service_area")
public class HubServiceArea {

    @EmbeddedId
    private HubServiceAreaId id;

    @MapsId("hubLocationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hub_location_id")
    private Location hub;

    @MapsId("provinceCode")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "province_code")
    private Province province;
}
