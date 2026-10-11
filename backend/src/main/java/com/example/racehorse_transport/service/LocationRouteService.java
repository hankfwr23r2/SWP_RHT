package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.LocationRoute;
import com.example.racehorse_transport.entity.LocationRouteId;
import java.util.List;
import java.util.Optional;

public interface LocationRouteService {
    List<LocationRoute> findAll();
    Optional<LocationRoute> findById(LocationRouteId id);
    LocationRoute save(LocationRoute entity);
    void deleteById(LocationRouteId id);
}
