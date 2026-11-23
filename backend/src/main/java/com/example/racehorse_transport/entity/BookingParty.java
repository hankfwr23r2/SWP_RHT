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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// Các bên trong đơn theo vai trò: người trả tiền, người gửi, người nhận, liên hệ khẩn cấp.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "booking_party")
public class BookingParty {

    @EmbeddedId
    private BookingPartyId id;

    @MapsId("bookingId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    // snapshot: thông tin các bên tại thời điểm đặt đơn, không đổi theo hồ sơ khách
    @Column(name = "full_name", length = 200, nullable = false)
    private String fullName;

    @Column(name = "phone", length = 20, nullable = false)
    private String phone;

    @Column(name = "id_type", length = 30)
    private String idType;

    @Column(name = "id_number", length = 50)
    private String idNumber;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id_country", length = 2)
    private String idCountry;

    @Column(name = "address", length = 500, nullable = false)
    private String address;
}
