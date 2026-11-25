package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.policy.DepartureDelayPolicy;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.service.CoordinatorAssignmentService;
import com.example.racehorse_transport.service.CoordinatorReminderService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CoordinatorReminderServiceImpl implements CoordinatorReminderService {
    private final DepartureRepository departures;
    private final SystemConfigService configs;
    private final CoordinatorAssignmentService assignments;
    private final NotificationService notifications;
    private final Clock clock;

    @Override
    @Transactional
    public void remindUnassigned() {
        var managers = assignments.activeManagerIds();
        if (managers.isEmpty()) {
            return;
        }
        var now = clock.instant();
        var today = now.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
        var cutoff = now.plus(Duration.ofHours(configs.getInt("COORDINATOR_ASSIGN_REMIND_HOURS")));
        var open = Set.of(DepartureStatus.OPEN, DepartureStatus.CLOSED);
        for (Long id : departures.findUnassignedReminderCandidates(open, now, cutoff, today)) {
            departures.findForUpdate(id)
                    .filter(d -> d.getCoordinator() == null && open.contains(d.getStatus()))
                    .filter(d -> d.getCoordinatorRemindedOn() == null || d.getCoordinatorRemindedOn().isBefore(today))
                    .filter(d -> {
                        var start = DepartureDelayPolicy.effectiveTime(d);
                        return !start.isBefore(now) && !start.isAfter(cutoff);
                    }).ifPresent(d -> {
                        notifications.notify(managers, null, "Chuyến chưa có điều phối",
                                d.getRoute().getCode() + " · " + d.getDepartureDate(), "/manager/departures");
                        d.setCoordinatorRemindedOn(today);
                    });
        }
    }
}
