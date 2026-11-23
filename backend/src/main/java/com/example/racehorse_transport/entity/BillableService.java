package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.BillingUnit;
import com.example.racehorse_transport.entity.enums.ServiceLayer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Dịch vụ tính tiền (bảng service). Đặt tên BillableService để không trùng annotation @Service của Spring. Đơn giá nằm ở price_item.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "billable_service")
public class BillableService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "code", length = 30, nullable = false)
    private String code;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "layer", length = 30, nullable = false)
    private ServiceLayer layer;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_unit", length = 30, nullable = false)
    private BillingUnit billingUnit;

    // Thu hộ, không tính biên lợi nhuận
    @Column(name = "is_pass_through", nullable = false)
    private Boolean passThrough = false;

    // Tự thêm vào báo giá, khách không phải chọn
    @Column(name = "is_auto_applied", nullable = false)
    private Boolean autoApplied = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private ActiveStatus status;
}
