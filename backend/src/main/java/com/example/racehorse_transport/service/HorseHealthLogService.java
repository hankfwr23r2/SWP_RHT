package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.HorseHealthLog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface HorseHealthLogService {
    List<HorseHealthLog> findAll();
    Optional<HorseHealthLog> findById(Integer id);
    HorseHealthLog save(HorseHealthLog entity);
    void deleteById(Integer id);

    // Tìm nhật ký sức khỏe theo mã ngựa
    List<HorseHealthLog> findByHorseId(Integer horseId);

    // Tìm nhật ký sức khỏe theo đơn hàng / chuyến đi
    List<HorseHealthLog> findByBookingId(Integer bookingId);

    // API dành cho Dev 4: Cập nhật tình trạng sức khỏe ngựa kèm các chỉ số
    HorseHealthLog updateHorseHealthStatusByEscort(Integer horseId, Integer bookingId, String healthStatus,
                                                   BigDecimal temperature, BigDecimal weight, String symptoms,
                                                   Integer escortStaffId);

    // Overload tương thích code ban đầu
    HorseHealthLog updateHorseHealthStatusByEscort(Integer horseId, Integer routeId, String healthStatus, String note);
}