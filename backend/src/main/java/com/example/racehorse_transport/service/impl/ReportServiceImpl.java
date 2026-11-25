package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.policy.IncidentPolicy;
import com.example.racehorse_transport.dto.report.OperationsSummaryResponseDto;
import com.example.racehorse_transport.dto.report.OperationsSummaryResponseDto.PayerAmountDto;
import com.example.racehorse_transport.dto.report.OperationsSummaryResponseDto.IncidentTypeCountDto;
import com.example.racehorse_transport.dto.report.TripReportResponseDto;
import com.example.racehorse_transport.dto.report.TripReportResponseDto.ReportCostDto;
import com.example.racehorse_transport.dto.report.TripReportResponseDto.ReportHealthDto;
import com.example.racehorse_transport.dto.report.TripReportResponseDto.ReportIncidentDto;
import com.example.racehorse_transport.dto.report.TripReportResponseDto.ReportRatingDto;
import com.example.racehorse_transport.dto.report.TripReportResponseDto.ReportStopDto;
import com.example.racehorse_transport.dto.report.TripReportRowResponseDto;
import com.example.racehorse_transport.entity.BookingRating;
import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.HorseLegType;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.ReportPolicy;
import com.example.racehorse_transport.repository.BookingRatingRepository;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.HorseHealthLogRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.ReportService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final Set<IncidentStatus> CLOSED_INCIDENTS = Set.of(IncidentStatus.RESOLVED, IncidentStatus.CLOSED);
    private static final Set<HorseLegType> HANDS_TO_CONSIGNEE = Set.of(HorseLegType.DELIVERY, HorseLegType.DIRECT);

    private final VehicleTripRepository trips;
    private final TripStopRepository stops;
    private final HorseLegRepository legs;
    private final IncidentRepository incidents;
    private final CostItemRepository costs;
    private final BookingRatingRepository ratings;
    private final HorseHealthLogRepository healthLogs;

    // Dữ liệu của các lượt xe trong báo cáo, gom theo lượt xe
    private record Facts(List<VehicleTrip> trips, Map<Long, List<TripStop>> stops, Map<Long, List<HorseLeg>> legs,
                         Map<Long, List<Incident>> incidents, Map<Long, List<CostItem>> costs, Map<Long, BookingRating> ratings) {

        <T> List<T> of(Map<Long, List<T>> rows, VehicleTrip trip) {
            return rows.getOrDefault(trip.getId(), List.of());
        }

        List<BookingRating> ratingsOf(Collection<HorseLeg> tripLegs) {
            return tripLegs.stream().map(leg -> leg.getBookingHorse().getBooking().getId()).distinct()
                    .map(ratings::get).filter(rating -> rating != null).toList();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripReportRowResponseDto> trips(Instant from, Instant to) {
        Facts facts = load(inRange(from, to));
        return facts.trips().stream().map(trip -> row(trip, facts)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TripReportResponseDto trip(Long vehicleTripId) {
        VehicleTrip trip = trips.findOneById(vehicleTripId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + vehicleTripId));
        Facts facts = load(List.of(trip));
        List<HorseLeg> tripLegs = facts.of(facts.legs(), trip);
        return new TripReportResponseDto(row(trip, facts),
                facts.of(facts.stops(), trip).stream().map(stop -> new ReportStopDto(stop.getSequenceNumber(), stop.getStopType(), nameOf(stop),
                        stop.getPlannedArrivalAt(), stop.getActualArrivalAt(), stop.getActualDepartureAt(),
                        ReportPolicy.isLate(stop.getPlannedArrivalAt(), stop.getActualArrivalAt()))).toList(),
                healthLogs.findByVehicleTrip_IdOrderByRecordedAt(trip.getId()).stream().map(log -> new ReportHealthDto(
                        log.getBookingHorse().getHorse().getName(), log.getRecordedAt(), log.getHealthStatus(), log.getTemperatureCelsius(),
                        log.getWaterIntake(), log.getFeedIntake(), log.getSymptoms())).toList(),
                facts.of(facts.incidents(), trip).stream().map(incident -> new ReportIncidentDto(incident.getId(), incident.getTitle(),
                        incident.getIncidentType(), incident.getCauseType(), incident.getStatus(), incident.getReportedAt(), incident.getResolvedAt())).toList(),
                facts.of(facts.costs(), trip).stream().map(cost -> new ReportCostDto(cost.getCategory(), cost.getDescription(), cost.getAmount(),
                        cost.getPayerType(), cost.getApprovedAt() != null)).toList(),
                facts.ratingsOf(tripLegs).stream().map(rating -> new ReportRatingDto(rating.getBooking().getCode(), rating.getScore(), rating.getComment())).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public OperationsSummaryResponseDto summary(Instant from, Instant to) {
        Facts facts = load(inRange(from, to));
        List<TripReportRowResponseDto> rows = facts.trips().stream().map(trip -> row(trip, facts)).toList();
        List<Incident> allIncidents = facts.incidents().values().stream().flatMap(List::stream).toList();
        List<CostItem> allCosts = facts.costs().values().stream().flatMap(List::stream).distinct().toList();
        List<BookingRating> allRatings = facts.ratingsOf(facts.legs().values().stream().flatMap(List::stream).toList());
        int arrived = rows.stream().mapToInt(TripReportRowResponseDto::arrivedStopCount).sum();
        int late = rows.stream().mapToInt(TripReportRowResponseDto::lateStopCount).sum();
        long delivered = facts.legs().values().stream().flatMap(List::stream)
                .filter(leg -> leg.getStatus() == HorseLegStatus.COMPLETED && HANDS_TO_CONSIGNEE.contains(leg.getLegType())).count();
        return new OperationsSummaryResponseDto(rows.size(), (int) rows.stream().filter(row -> row.status() == VehicleTripStatus.COMPLETED).count(),
                (int) delivered, arrived, late, ReportPolicy.onTimeRate(arrived, late),
                allIncidents.size(), (int) allIncidents.stream().filter(incident -> !CLOSED_INCIDENTS.contains(incident.getStatus())).count(),
                allIncidents.stream().collect(Collectors.groupingBy(incident -> incident.getIncidentType().name(), TreeMap::new, Collectors.counting()))
                        .entrySet().stream().map(e -> new IncidentTypeCountDto(e.getKey(), e.getValue())).toList(),
                sum(allCosts),
                allCosts.stream().collect(Collectors.groupingBy(cost -> cost.getPayerType().name(), TreeMap::new,
                                Collectors.reducing(BigDecimal.ZERO, CostItem::getAmount, BigDecimal::add)))
                        .entrySet().stream().map(e -> new PayerAmountDto(e.getKey(), e.getValue())).toList(),
                average(allRatings), allRatings.size());
    }

    private List<VehicleTrip> inRange(Instant from, Instant to) {
        return trips.findByPlannedStartAtGreaterThanEqualAndPlannedStartAtLessThanAndStatusNotOrderByPlannedStartAtAscIdAsc(from, to, VehicleTripStatus.CANCELLED);
    }

    private Facts load(List<VehicleTrip> tripList) {
        List<Long> ids = tripList.stream().map(VehicleTrip::getId).toList();
        if (ids.isEmpty()) return new Facts(tripList, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        List<HorseLeg> tripLegs = legs.findByVehicleTrip_IdIn(ids);
        List<Long> bookingIds = tripLegs.stream().map(leg -> leg.getBookingHorse().getBooking().getId()).distinct().toList();
        return new Facts(tripList,
                stops.findByVehicleTrip_IdInOrderByVehicleTrip_IdAscSequenceNumberAsc(ids).stream().collect(Collectors.groupingBy(stop -> stop.getVehicleTrip().getId())),
                tripLegs.stream().collect(Collectors.groupingBy(leg -> leg.getVehicleTrip().getId())),
                incidents.findByVehicleTrip_IdIn(ids).stream().sorted(IncidentPolicy.DISPLAY_ORDER)
                        .collect(Collectors.groupingBy(incident -> incident.getVehicleTrip().getId())),
                costs.findOfTrips(ids).stream().collect(Collectors.groupingBy(ReportServiceImpl::tripOf)),
                bookingIds.isEmpty() ? Map.of() : ratings.findByBookingIdIn(bookingIds).stream().collect(Collectors.toMap(BookingRating::getBookingId, r -> r)));
    }

    private static TripReportRowResponseDto row(VehicleTrip trip, Facts facts) {
        List<TripStop> tripStops = facts.of(facts.stops(), trip);
        List<HorseLeg> tripLegs = facts.of(facts.legs(), trip);
        int arrived = (int) tripStops.stream().filter(stop -> stop.getActualArrivalAt() != null).count();
        int late = (int) tripStops.stream().filter(stop -> ReportPolicy.isLate(stop.getPlannedArrivalAt(), stop.getActualArrivalAt())).count();
        int horses = (int) tripLegs.stream().filter(leg -> leg.getStatus() != HorseLegStatus.CANCELLED)
                .map(leg -> leg.getBookingHorse().getId()).distinct().count();
        return new TripReportRowResponseDto(trip.getId(), trip.getCode(), trip.getTripType(), trip.getStatus(), trip.getVehicle().getLicensePlate(),
                trip.getStartLocation().getName(), trip.getEndLocation().getName(), trip.getPlannedStartAt(), trip.getActualStartAt(), trip.getActualEndAt(),
                tripStops.size(), arrived, late, horses, facts.of(facts.incidents(), trip).size(), sum(facts.of(facts.costs(), trip)),
                average(facts.ratingsOf(tripLegs)));
    }

    private static Long tripOf(CostItem cost) {
        return cost.getVehicleTrip() != null ? cost.getVehicleTrip().getId() : cost.getIncident().getVehicleTrip().getId();
    }

    private static String nameOf(TripStop stop) {
        return stop.getLocation() != null ? stop.getLocation().getName() : stop.getBookingAddress().getAddressLine();
    }

    private static BigDecimal sum(List<CostItem> items) {
        return items.stream().map(CostItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Double average(List<BookingRating> list) {
        return list.isEmpty() ? null : list.stream().mapToInt(BookingRating::getScore).average().orElseThrow();
    }
}
