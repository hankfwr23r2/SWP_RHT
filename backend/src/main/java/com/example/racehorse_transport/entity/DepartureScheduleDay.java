package com.example.racehorse_transport.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Lịch chạy vào thứ mấy.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "departure_schedule_day")
public class DepartureScheduleDay {

    @EmbeddedId
    private DepartureScheduleDayId id;

    @MapsId("scheduleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id")
    private DepartureSchedule schedule;
}
