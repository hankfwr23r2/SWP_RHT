package com.example.racehorse_transport.service;

import java.util.List;

/** Nhắc chọn lại lịch và kết thúc hạn giữ giá sau khi không đón được ngựa. */
public interface PickupMissFollowUpService {
    List<Long> candidates();

    void process(Long bookingId);
}
