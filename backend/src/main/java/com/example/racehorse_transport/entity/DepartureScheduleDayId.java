package com.example.racehorse_transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

// Khóa của departure_schedule_day. dayOfWeek theo ISO: 1 = thứ Hai, 7 = Chủ nhật.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class DepartureScheduleDayId implements Serializable {

    @Column(name = "departure_schedule_id", nullable = false)
    private Long departureScheduleId;

    @Column(name = "day_of_week", nullable = false)
    private Short dayOfWeek;
}
