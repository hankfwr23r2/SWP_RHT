package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface StaffWorkRepository extends Repository<Booking, Long> {
    interface StatusCount {
        BookingStatus getStatus();
        long getTotal();
    }

    @Query("select b.status as status, count(b) as total from Booking b group by b.status")
    List<StatusCount> countBookingsByStatus();

    @Query(value = """
            select
              (select count(*) from booking where specialist_id is null and status = 'PENDING_INTAKE')
              + (select count(*) from departure where coordinator_id is null and status in ('OPEN', 'CLOSED'))
                as assignments,
              (select count(*) from departure where status in ('OPEN', 'CLOSED') and run_confirmed_at is null
                and case when planned_departure_at > :now then planned_departure_at
                         else coalesce(revised_departure_at, planned_departure_at) end
                    > :now
                and case when planned_departure_at > :now then planned_departure_at
                         else coalesce(revised_departure_at, planned_departure_at) end
                    <= :decisionCutoff) as decisions,
              (select count(*) from payment where payment_type = 'REFUND' and status = 'PENDING') as refunds,
              (select count(*) from incident i where i.status <> 'CLOSED' and (
                exists (select 1 from incident_plan p where p.incident_id = i.id and p.status = 'PROPOSED')
                or exists (select 1 from cost_item c where c.incident_id = i.id and c.approved_at is null))) as incidents
            """, nativeQuery = true)
    ManagerCounts countManagerWork(Instant now, Instant decisionCutoff);

    interface ManagerCounts {
        long getAssignments();
        long getDecisions();
        long getRefunds();
        long getIncidents();
    }

    @Query(value = """
            select count(*) from booking b
            where b.status in ('CONFIRMED', 'IN_OPERATION')
              and exists (select 1 from booking_horse h
                cross join (values ('DIRECT'), ('PICKUP'), ('LINEHAUL'), ('DELIVERY')) required(kind)
                where h.booking_id = b.id and h.status = 'ACTIVE'
                  and ((b.departure_id is null and kind = 'DIRECT')
                    or (b.departure_id is not null and
                       (kind = 'LINEHAUL' or (kind = 'PICKUP' and b.pickup_method <> 'SELF'
                         and h.pickup_miss_choice is distinct from 'SELF_DELIVERY')
                        or (kind = 'DELIVERY' and b.delivery_method <> 'SELF'))))
                  and not exists (select 1 from horse_leg l where l.booking_horse_id = h.id
                    and l.leg_type = kind and l.status not in ('CANCELLED', 'FAILED')))
            """, nativeQuery = true)
    long countWaitingForVehicle();

    @Query(value = """
            select
              (select count(*) from vehicle_trip t where t.status in ('PLANNED', 'DISPATCHED')
                and (not exists (select 1 from crew_assignment c where c.vehicle_trip_id = t.id
                    and c.crew_role = 'DRIVER' and c.status <> 'RELEASED')
                  or (exists (select 1 from horse_leg h where h.vehicle_trip_id = t.id
                      and h.status not in ('CANCELLED', 'FAILED'))
                    and not exists (select 1 from crew_assignment c where c.vehicle_trip_id = t.id
                      and c.crew_role = 'ESCORT' and c.status <> 'RELEASED')))) as crew,
              (select count(*) from incident where status = 'REPORTED' and incident_type <> 'COMPLAINT') as incidents,
              (select count(*) from vehicle_trip t
                join lateral (select s.* from trip_stop s where s.vehicle_trip_id = t.id
                  order by s.sequence_number desc limit 1) last_stop on true
                left join lateral (select s.actual_departure_at - s.planned_departure_at as delay
                  from trip_stop s where s.vehicle_trip_id = t.id and s.sequence_number < last_stop.sequence_number
                    and s.actual_departure_at is not null order by s.sequence_number desc limit 1) previous on true
                where t.status = 'IN_PROGRESS' and last_stop.actual_arrival_at is null
                  and last_stop.planned_arrival_at + greatest(interval '0 minutes', previous.delay) < :returnCutoff
                  and not exists (select 1 from horse_leg l where l.vehicle_trip_id = t.id
                    and (l.status in ('PLANNED', 'IN_PROGRESS') or (l.status = 'FAILED'
                      and not exists (select 1 from handover_horse hh join handover h on h.id = hh.handover_id
                        where hh.horse_leg_id = l.id and h.handover_type = 'HUB_IN' and h.status = 'DONE'))))) as late
            """, nativeQuery = true)
    CoordinatorCounts countCoordinatorWork(Instant returnCutoff);

    interface CoordinatorCounts {
        long getCrew();
        long getIncidents();
        long getLate();
    }
}
