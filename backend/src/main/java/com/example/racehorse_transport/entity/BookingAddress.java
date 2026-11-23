package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.AddressRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

// Điểm đón và giao của đơn (snapshot). Là đích FK của trip_stop và horse_leg.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "booking_address")
public class BookingAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 30, nullable = false)
    private AddressRole role;

    // snapshot: địa chỉ đón và giao tại thời điểm đặt đơn
    @Column(name = "address_line", length = 500, nullable = false)
    private String addressLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "province_code", nullable = false)
    private Province province;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_code", nullable = false)
    private Country country;

    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "contact_name", length = 200, nullable = false)
    private String contactName;

    @Column(name = "contact_phone", length = 20, nullable = false)
    private String contactPhone;

    @Column(name = "access_note", columnDefinition = "text")
    private String accessNote;

    // Nếu điểm này trùng một location của mạng lưới, ví dụ trường đua
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "location_id")
    private Location location;

    // Chép từ sổ địa chỉ nào
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "source_address_id")
    private PartyAddress sourceAddress;
}
