package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.TripStop;

// Kiểm trước khi xe rời một điểm dừng (B2, B3): giao nhận dở, nơi nhận ngựa đã trả đủ (#32), trạm nghỉ đã ghi ăn uống và nghỉ đủ (#34)
public interface StopReadinessService {

    boolean isPaidInFullForDeparture(Long bookingId);

    // Trả về true khi xe được phép rời sớm ở điểm nghỉ đêm.
    boolean requireCanLeave(TripStop stop, String earlyReason, Long actorUserId);
}
