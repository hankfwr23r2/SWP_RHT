package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.IdentityDocumentType;
import com.example.racehorse_transport.entity.enums.PartyKind;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Một bên (cá nhân hoặc tổ chức): khách, người nhận, đại lý, trại cách ly, thú y, cứu hộ, bảo hiểm.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "party")
public class Party {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 30, nullable = false)
    private PartyKind kind;

    @Column(name = "display_name", length = 200, nullable = false)
    private String displayName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "address", length = 500)
    private String address;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_code", nullable = false)
    private Country country;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_type", length = 30)
    private IdentityDocumentType idType;

    @Column(name = "id_number", length = 50)
    private String idNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_country")
    private Country idCountry;

    @Column(name = "tax_code", length = 30)
    private String taxCode;

    // Bên này có tài khoản đăng nhập (khách hàng)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private ActiveStatus status;
}
