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
public class LocationDTO implements Serializable {
    private Integer id;
    private String locationType;
    private String locationName;
    private String address;
    private String city;
    private String province;
    private String country;
}
