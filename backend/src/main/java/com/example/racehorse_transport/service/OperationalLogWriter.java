package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.Log;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.LogRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OperationalLogWriter {
    private final LogRepository logs;
    private final BookingRepository bookings;
    private final StaffRepository staff;
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String target, Integer key, String note, String actor) {
        Log entry = new Log();
        entry.setLogType(action);
        entry.setTargetTable(target);
        entry.setTargetRecordId(key);
        entry.setNote(note);
        entry.setCreatedAt(Instant.now());
        if (actor == null) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) actor = auth.getName();
        }
        entry.setActorUsername(actor);
        if (actor != null) staff.findByUser_Username(actor).ifPresent(entry::setStaffID);
        if ("BOOKING".equals(target) && key != null) bookings.findById(key).ifPresent(entry::setBookingID);
        logs.save(entry);
    }
}
