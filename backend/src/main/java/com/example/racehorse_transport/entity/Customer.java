package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.CustomerType;
import com.example.racehorse_transport.entity.enums.IdentityDocumentType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Hồ sơ khách hàng (1–1 với app_user). Email và trạng thái lấy từ app_user.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @Column(name = "user_id")
    private Long userId;

    // Khóa chính kế thừa khóa của tài khoản
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", length = 30, nullable = false)
    private CustomerType customerType;

    // Tên người hoặc tổ chức
    @Column(name = "full_name", length = 200, nullable = false)
    private String fullName;

    @Column(name = "phone", length = 20)
    private String phone;

    // Địa chỉ liên lạc hoặc xuất hóa đơn
    @Column(name = "address", length = 500)
    private String address;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_code", nullable = false)
    private Country country;

    // Giấy tờ: khai đủ cả 3 field hoặc bỏ trống cả 3
    @Enumerated(EnumType.STRING)
    @Column(name = "identity_document_type", length = 30)
    private IdentityDocumentType identityDocumentType;

    @Column(name = "identity_document_number", length = 50)
    private String identityDocumentNumber;

    // Nước cấp giấy tờ
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "identity_document_country_code")
    private Country identityDocumentCountry;

    @Column(name = "tax_code", length = 30)
    private String taxCode;
}
