package com.example.racehorse_transport.service;

import java.util.List;

/** Gỡ các chỗ làm đơn đã giao bị treo: chi phí chưa duyệt, sự cố chưa đóng, khoản hoàn quá hạn. */
public interface SettlementFollowUpService {
    List<Long> deliveredBookings();

    void processDelivered(Long bookingId);

    List<Long> resolvedIncidents();

    void closeResolvedIncident(Long incidentId);

    void remindOverdueRefunds();
}
