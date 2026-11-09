package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "SERVICE_BOOKING")
@Check(
        name = "CK_SERVICE_BOOKING_VALUES",
        constraints = "quantity > 0 AND unit_price >= 0 AND subtotal >= 0"
)
public class ServiceBooking {

    @EmbeddedId
    private ServiceBookingId id;

    @MapsId("bookingID")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "BookingID", nullable = false)
    private Booking bookingID;

    @MapsId("horseID")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "HorseID", nullable = false)
    private Horse horseID;

    @MapsId("serviceID")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ServiceID", nullable = false)
    private Service serviceID;

    @NotNull
    @Min(1)
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    @Column(name = "subtotal", nullable = false, precision = 18, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "selection_category", length = 50)
    private String selectionCategory;
    @Column(name = "snapshot_service_code", length = 100)
    private String snapshotServiceCode;
    @Column(name = "snapshot_service_name", length = 255)
    private String snapshotServiceName;
    @Column(name = "snapshot_category_id")
    private Integer snapshotCategoryId;
    @Column(name = "snapshot_category_name", length = 255)
    private String snapshotCategoryName;
    @Column(name = "snapshot_billing_unit", length = 100)
    private String snapshotBillingUnit;
    @Column(name = "snapshot_description", columnDefinition = "TEXT")
    private String snapshotDescription;
    @Column(name = "snapshot_price", precision = 18, scale = 2)
    private BigDecimal snapshotPrice;
}
