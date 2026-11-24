package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.DepartureDecisionService;
import com.example.racehorse_transport.service.DepartureService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Sinh chuyến từ lịch mỗi ngày (mặc định 00:15 giờ Việt Nam), đóng chuyến qua giờ chốt và nhả chỗ ở mốc quyết định (mặc định mỗi 15 phút).
@Component
@RequiredArgsConstructor
public class DepartureScheduler {

    private final DepartureService departureService;
    private final DepartureDecisionService decisionService;

    @Scheduled(cron = "${app.departure.generate-cron:0 15 0 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void generateFromSchedules() {
        departureService.generateFromSchedules();
    }

    @Scheduled(fixedDelayString = "${app.departure.close-delay-ms:900000}", initialDelayString = "${app.departure.close-delay-ms:900000}")
    public void closePastCutoff() {
        departureService.closePastCutoff();
    }

    @Scheduled(fixedDelayString = "${app.departure.release-delay-ms:900000}", initialDelayString = "${app.departure.release-delay-ms:900000}")
    public void releaseUnconfirmed() {
        decisionService.releaseUnconfirmed();
    }
}
