package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.route.RoadRouteRequestDto;
import com.example.racehorse_transport.dto.route.RoadRouteResponseDto;
import com.example.racehorse_transport.dto.route.RoutePreviewRequestDto;
import com.example.racehorse_transport.dto.route.RoutePreviewResponseDto;

public interface RoutePreviewService {

    RoutePreviewResponseDto preview(RoutePreviewRequestDto request);

    RoadRouteResponseDto roadRoute(RoadRouteRequestDto request);
}
