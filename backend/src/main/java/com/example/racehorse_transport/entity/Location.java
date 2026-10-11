package com.example.racehorse_transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "LOCATION")
public class Location {
    @Id
    @Column(name = "LocationID", nullable = false)
    private Integer id;

    @Size(max = 100)
    @Column(name = "LocationType", length = 100)
    private String locationType;

    @Size(max = 255)
    @Nationalized
    @Column(name = "LocationName")
    private String locationName;

    @Size(max = 255)
    @Nationalized
    @Column(name = "address")
    private String address;

    @Size(max = 100)
    @Nationalized
    @Column(name = "city", length = 100)
    private String city;

    @Size(max = 100)
    @Nationalized
    @Column(name = "province", length = 100)
    private String province;

    @Size(max = 100)
    @Nationalized
    @Column(name = "country", length = 100)
    private String country;


}