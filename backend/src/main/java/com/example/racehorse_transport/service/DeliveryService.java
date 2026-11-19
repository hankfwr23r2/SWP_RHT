package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayloadDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.dto.field.DeliveryArriveRequestDto;
import com.example.racehorse_transport.dto.field.DeliveryTimeoutRequestDto;
import com.example.racehorse_transport.dto.field.HandoverCompleteRequestDto;
import com.example.racehorse_transport.dto.field.ReturnToHqRequestDto;
import com.example.racehorse_transport.entity.Booking;

public interface DeliveryService {

    // 1. Kíp xe check-in đến điểm giao đích & kích hoạt đếm thời gian
    BookingResponseDto arriveDelivery(Integer bookingId, DeliveryArriveRequestDto request);

    // 2. Nghiệm thu & Ký Biên bản bàn giao pháp lý (không rating tài xế)
    BookingResponseDto completeHandover(Integer bookingId, HandoverCompleteRequestDto request);

    // 3. Xử lý ngoại lệ: Người nhận không nhận được ngựa đúng hạn (quá 2 giờ)
    BookingResponseDto reportDeliveryTimeout(Integer bookingId, DeliveryTimeoutRequestDto request, String username, String role);

    // Phát hành quyết toán và thanh toán số dư: xem SettlementService

    // 4. Kíp xe xác nhận xe và nhân sự đã về tới Trụ sở chính Việt Nam
    void returnToHq(ReturnToHqRequestDto request);

    void completeTrip(Booking booking, BookingDetailsPayloadDto payload, VehicleTripDto trip,
                      String actor, String photo, long time);
}
