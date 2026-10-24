package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.CreateRestStopRequest;
import com.example.racehorse_transport.entity.Location;

import java.util.List;

public interface LocationService {
    List<Location> findLocations(String type, String country);
    Location createRestStop(CreateRestStopRequest request);
    Location updateRestStop(Integer id, CreateRestStopRequest request);
    void deleteRestStop(Integer id);
}
