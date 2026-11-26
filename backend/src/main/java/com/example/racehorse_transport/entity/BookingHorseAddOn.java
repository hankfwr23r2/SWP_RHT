package com.example.racehorse_transport.entity;

import jakarta.persistence.Column;
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

// Dịch vụ thêm cho từng con (bảng booking_horse_service). Đặt tên BookingHorseAddOn để không trùng interface BookingHorseService.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "booking_horse_service")
public class BookingHorseAddOn {

    @EmbeddedId
    private BookingHorseAddOnId id;

    @MapsId("bookingHorseId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_horse_id")
    private BookingHorse bookingHorse;

    // Chỉ chọn được dịch vụ có layer = ADD_ON (kiểm ở billableService)
    @MapsId("billableServiceId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "billable_service_id")
    private BillableService billableService;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;
}
