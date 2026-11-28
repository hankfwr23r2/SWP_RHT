package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.geocode.GeocodeResultResponseDto;
import java.util.List;

public interface GeocodeService {
    List<GeocodeResultResponseDto> search(String query, String countryCode);
}
