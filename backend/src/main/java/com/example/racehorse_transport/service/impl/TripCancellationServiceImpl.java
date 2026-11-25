package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.dispatch.VehicleTripCancelRequestDto;
import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripCancellationServiceImpl implements TripCancellationService {
    private final VehicleTripRepository trips;
    private final HorseLegRepository legs;
    private final CrewAssignmentRepository crew;
    private final DepartureService departures;
    private final CoordinatorAssignmentService assignments;
    private final NotificationService notifications;
    private final DispatchService dispatch;

    @Override
    @Transactional
    public com.example.racehorse_transport.dto.dispatch.VehicleTripResponseDto cancel(Long id, VehicleTripCancelRequestDto request, String username) {
        assignments.requireTripWrite(id, username);
        VehicleTrip trip = trips.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + id));
        if (trip.getStatus() != VehicleTripStatus.PLANNED && trip.getStatus() != VehicleTripStatus.DISPATCHED) {
            throw new DispatchRuleException("status", "Chỉ hủy được lượt xe chưa chạy.", true);
        }
        if (legs.existsByVehicleTrip_IdAndStatusNot(id, HorseLegStatus.CANCELLED)) {
            throw new DispatchRuleException("status", "Lượt xe còn ngựa. Gỡ ngựa khỏi lượt trước khi hủy.", true);
        }
        trip.setStatus(VehicleTripStatus.CANCELLED);
        if (trip.getTripType() == VehicleTripType.LINEHAUL) {
            departures.linehaulCompleted(trip.getDeparture().getId());
        }
        for (CrewAssignment assignment : crew.findByVehicleTrip_IdInOrderById(List.of(id))) {
            if (assignment.getStatus() == CrewAssignmentStatus.RELEASED) continue;
            assignment.setStatus(CrewAssignmentStatus.RELEASED);
            assignment.setNote(request.reason().strip());
            notifications.notify(List.of(assignment.getStaff().getUserId()), null,
                    "Lượt xe đã hủy",
                    "Lượt xe " + trip.getCode() + " đã hủy. Lý do: " + request.reason().strip(),
                    "/my-trips");
        }
        return dispatch.trip(id);
    }
}
