package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.DocumentStatus;

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

import org.hibernate.annotations.Generated;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// Tệp chứng từ. Mỗi chứng từ thuộc đúng một đối tượng.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "document")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_type_id", nullable = false)
    private DocumentType documentType;

    // Chứng từ này đáp ứng yêu cầu giấy tờ nào
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "document_requirement_id")
    private DocumentRequirement documentRequirement;

    @Column(name = "file_url", length = 1000, nullable = false)
    private String fileUrl;

    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private AppUser uploadedBy;

    // DB đặt giá trị mặc định now() lúc thêm dòng
    @Generated
    @Column(name = "uploaded_at", nullable = false, insertable = false, updatable = false)
    private Instant uploadedAt;

    // Thời điểm và tọa độ chụp, với ảnh hiện trường
    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private DocumentStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reviewed_by")
    private Staff reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    // Chủ sở hữu: đúng một cột dưới đây có giá trị (CHECK ở DB), cột nào phải khớp document_type.owner_type (kiểm ở DocumentService)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "horse_id")
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "booking_horse_id")
    private BookingHorse bookingHorse;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "horse_leg_id")
    private HorseLeg horseLeg;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "vehicle_trip_id")
    private VehicleTrip vehicleTrip;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "horse_insurance_id")
    private HorseInsurance horseInsurance;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "cost_item_id")
    private CostItem costItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "handover_id")
    private Handover handover;

    // Chặng của con ngựa trong lần giao nhận chứa ảnh.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_horse_leg_id")
    private HorseLeg evidenceHorseLeg;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "party_id")
    private Party party;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "customer_id")
    private Customer customer;
}
