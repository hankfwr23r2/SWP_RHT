package com.example.racehorse_transport.service;

// Giao thất bại (B5): chờ người nhận, quá giờ chờ miễn phí thì giao nhận TIMEOUT, chặng FAILED, sự cố HANDOVER_TIMEOUT.
public interface DeliveryFailureService {

    // Tài xế bấm bắt đầu chờ ở lần giao đang mở; trả id lượt xe
    Long startWaiting(Long handoverId, String username);

    // Không gặp người nhận sau khi hết giờ chờ miễn phí; trả id lượt xe
    Long fail(Long handoverId, String username);

    // Scheduler: xử lý mọi lần chờ đã quá giờ. Trả về số lần giao bị đánh dấu TIMEOUT
    int timeoutExpired();
}
