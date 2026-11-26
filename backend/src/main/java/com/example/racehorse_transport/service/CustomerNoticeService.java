package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.HorseStay;

// Chuông báo khách theo tiến độ đơn (MAN_HINH 3.4, 3.5c, 3.9). Gọi trong transaction của thao tác gốc.
public interface CustomerNoticeService {
    void clearHubAppointmentsAndNotify(Long bookingId, int removedLegs);

    // Khách vừa gửi đơn: báo khách đã nhận đơn, báo chuyên viên có đơn chờ tiếp nhận
    void bookingSubmitted(Booking booking);

    // Ngựa vừa vào lượt đón, giao, chạy thẳng: báo khung giờ xe đến, hoặc giờ đổi quá ngưỡng. Mỗi đơn mỗi lượt một lần
    void legAssigned(HorseLeg leg);

    // Đã hẹn giờ khách đưa ngựa tới hub
    void dropOffAppointed(HorseStay stay);

    // Đã hẹn giờ người nhận tới hub
    void collectionAppointed(HorseStay stay);
}
