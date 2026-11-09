package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.example.racehorse_transport.util.BookingDetailsCodec;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "BOOKING")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BookingID", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CustomerID")
    private Customer customerID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RouteID")
    private Route routeID;

    @Column(name = "BookingDate")
    private Instant bookingDate;

    @Column(name = "DepartureDate")
    private Instant departureDate;

    @Column(name = "TotalBookingPrice", precision = 18, scale = 2)
    private BigDecimal totalBookingPrice;

    @Size(max = 50)
    @Column(name = "Status", length = 50)
    private String status;

    @Size(max = 50)
    @Column(name = "BookingType", length = 50)
    private String bookingType; // domestic, international

    @Size(max = 255)
    @Nationalized
    @Column(name = "ConsignorName")
    private String consignorName;

    @Size(max = 50)
    @Column(name = "ConsignorPhone", length = 50)
    private String consignorPhone;

    @Size(max = 255)
    @Nationalized
    @Column(name = "ConsigneeName")
    private String consigneeName;

    @Size(max = 50)
    @Column(name = "ConsigneePhone", length = 50)
    private String consigneePhone;

    // CCCD / MST / Hộ chiếu (bắt buộc khi tạo đơn mới) và địa chỉ của người gửi, người nhận
    @Size(max = 50)
    @Column(name = "ConsignorIdNumber", length = 50)
    private String consignorIdNumber;

    @Column(name = "ConsignorIdCountry", length = 2)
    private String consignorIdCountry;

    @Column(name = "ConsignorIdType", length = 20)
    private String consignorIdType;

    @Size(max = 255)
    @Nationalized
    @Column(name = "ConsignorAddress")
    private String consignorAddress;

    @Size(max = 50)
    @Column(name = "ConsigneeIdNumber", length = 50)
    private String consigneeIdNumber;

    @Column(name = "ConsigneeIdCountry", length = 2)
    private String consigneeIdCountry;

    @Column(name = "ConsigneeIdType", length = 20)
    private String consigneeIdType;

    @Size(max = 255)
    @Nationalized
    @Column(name = "ConsigneeAddress")
    private String consigneeAddress;

    @Size(max = 255)
    @Nationalized
    @Column(name = "PickupAddress")
    private String pickupAddress;

    @Size(max = 255)
    @Nationalized
    @Column(name = "DropoffAddress")
    private String dropoffAddress;

    @Column(name = "TotalHorses")
    private Integer totalHorses;

    @Size(max = 50)
    @Column(name = "WaybillNo", length = 50)
    private String waybillNo;

    @Column(name = "DetailsJson", columnDefinition = "TEXT")
    private String detailsJson;

    @Version
    @Builder.Default
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Builder.Default
    @Column(name = "details_normalized", nullable = false)
    private boolean detailsNormalized = false;

    @Column(name = "horses_present")
    private Boolean horsesPresent;

    @Column(name = "origin_country", length = 100)
    private String originCountry;

    @Column(name = "dest_country", length = 100)
    private String destCountry;

    @Column(name = "gate_name", length = 255)
    private String gateName;

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "bookingID", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingHorse> bookingHorses = new ArrayList<>();

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "bookingID", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServiceBooking> serviceBookings = new ArrayList<>();

    /** Compatibility view for existing workflow services; JPA persists the backing field. */
    public String getDetailsJson() {
        return BookingDetailsCodec.read(this);
    }

    public void setDetailsJson(String value) {
        BookingDetailsCodec.write(this, value);
    }

    @JsonIgnore
    public String getStoredDetailsJson() {
        return detailsJson;
    }

    public void setStoredDetailsJson(String value) {
        detailsJson = value;
    }

    @PrePersist
    private void normalizeInitialDetails() {
        if (!detailsNormalized) BookingDetailsCodec.write(this, detailsJson);
    }

    public static class BookingBuilder {
        public Booking build() {
            Booking b = new Booking();
            b.setId(this.id);
            b.setCustomerID(this.customerID);
            b.setRouteID(this.routeID);
            b.setBookingDate(this.bookingDate);
            b.setDepartureDate(this.departureDate);
            b.setTotalBookingPrice(this.totalBookingPrice);
            b.setStatus(this.status);
            b.setBookingType(this.bookingType);
            b.setConsignorName(this.consignorName);
            b.setConsignorPhone(this.consignorPhone);
            b.setConsigneeName(this.consigneeName);
            b.setConsigneePhone(this.consigneePhone);
            b.setConsignorIdNumber(this.consignorIdNumber);
            b.setConsignorIdCountry(this.consignorIdCountry);
            b.setConsignorIdType(this.consignorIdType);
            b.setConsignorAddress(this.consignorAddress);
            b.setConsigneeIdNumber(this.consigneeIdNumber);
            b.setConsigneeIdCountry(this.consigneeIdCountry);
            b.setConsigneeIdType(this.consigneeIdType);
            b.setConsigneeAddress(this.consigneeAddress);
            b.setPickupAddress(this.pickupAddress);
            b.setDropoffAddress(this.dropoffAddress);
            b.setTotalHorses(this.totalHorses);
            b.setWaybillNo(this.waybillNo);
            b.setVersion(this.version$set ? this.version$value : 0L);
            b.setDetailsNormalized(this.detailsNormalized$set ? this.detailsNormalized$value : false);
            b.setHorsesPresent(this.horsesPresent);
            b.setOriginCountry(this.originCountry);
            b.setDestCountry(this.destCountry);
            b.setGateName(this.gateName);
            b.setBookingHorses(this.bookingHorses$set ? this.bookingHorses$value : new ArrayList<>());
            b.setServiceBookings(this.serviceBookings$set ? this.serviceBookings$value : new ArrayList<>());
            if (this.detailsJson != null) {
                b.setDetailsJson(this.detailsJson);
            }
            return b;
        }
    }
}
