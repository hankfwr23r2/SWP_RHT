package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.network.DepartureScheduleRequestDto;
import com.example.racehorse_transport.dto.network.DepartureScheduleResponseDto;
import com.example.racehorse_transport.dto.network.RouteRequestDto;
import com.example.racehorse_transport.dto.network.RouteResponseDto;
import java.util.List;

// Tuyến trục chính, điểm dừng và lịch chạy định kỳ. Chủ sở hữu: luồng A (A1).
public interface RouteService {

    // Danh sách tuyến, không kèm điểm dừng
    List<RouteResponseDto> routes();

    // Chi tiết tuyến kèm điểm dừng theo thứ tự
    RouteResponseDto route(Long id);

    RouteResponseDto createRoute(RouteRequestDto request);

    // Cập nhật thông tin và thay toàn bộ điểm dừng
    RouteResponseDto updateRoute(Long id, RouteRequestDto request);

    RouteResponseDto activateRoute(Long id);

    RouteResponseDto deactivateRoute(Long id);

    // routeId null nghĩa là mọi tuyến
    List<DepartureScheduleResponseDto> schedules(Long routeId);

    DepartureScheduleResponseDto createSchedule(DepartureScheduleRequestDto request);

    DepartureScheduleResponseDto updateSchedule(Long id, DepartureScheduleRequestDto request);

    DepartureScheduleResponseDto pauseSchedule(Long id);

    DepartureScheduleResponseDto resumeSchedule(Long id);

    // Kết thúc lịch; không mở lại được
    DepartureScheduleResponseDto endSchedule(Long id);
}
