package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.geocode.GeocodeResultResponseDto;
import java.util.List;

import com.example.racehorse_transport.client.GeocodeClient;
import com.example.racehorse_transport.service.GeocodeService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeocodeServiceImpl implements GeocodeService {
    private final GeocodeClient client;

    @Override
    public List<GeocodeResultResponseDto> search(String query, String countryCode) {
        return client.search(query.strip(), countryCode.toLowerCase(Locale.ROOT));
    }
}
