package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.LocationRequest;
import com.example.racehorse_transport.entity.Location;
import java.util.List;

public interface LocationService {
    List<Location> findLocations(String type, String country, boolean includeInactive);
    Location createLocation(LocationRequest request, String changedBy);
    Location updateLocation(Integer id, LocationRequest request, String changedBy);
    Location changeStatus(Integer id, String status, String changedBy);
}
