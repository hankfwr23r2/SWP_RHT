package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.policy.TripReturnPolicy.Progress;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

public interface TripReturnService {
    Map<Long, Progress> progress(List<VehicleTrip> trips, Map<Long, List<TripStop>> stopsByTrip);

    Set<Long> photographedTripIds(Collection<Long> tripIds);

    void attachPhoto(Long tripId, MultipartFile file, Long actorId);

    void requirePhoto(Long tripId);

    void remindLateReturns();
}
