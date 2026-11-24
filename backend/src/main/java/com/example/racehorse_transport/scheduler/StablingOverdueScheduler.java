package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.HorseStayService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// B5: mỗi sáng nhắc khách và quản lý về ngựa giữ tại hub sau giao thất bại đã quá hạn
@Component
@RequiredArgsConstructor
public class StablingOverdueScheduler {

    private final HorseStayService horseStays;

    @Scheduled(cron = "${app.stabling.overdue-cron:0 0 8 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void remindOverdue() {
        horseStays.remindOverdueStabling();
    }
}
