package com.example.racehorse_transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// Đơn giá của một dịch vụ trong bảng giá, theo tuyến, hạng xe, loại khoang, bậc km.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "price_item")
public class PriceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "price_list_id", nullable = false)
    private PriceList priceList;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private BillableService service;

    // Điều kiện áp dụng: NULL nghĩa là áp cho mọi giá trị
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "route_id")
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "vehicle_class_id")
    private VehicleClass vehicleClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "stall_type")
    private StallType stallType;

    @Column(name = "km_from", precision = 8, scale = 2)
    private BigDecimal kmFrom;

    @Column(name = "km_to", precision = 8, scale = 2)
    private BigDecimal kmTo;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;
}
