package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.QuotationLineType;

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
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;

// Một dòng báo giá. Đơn giá là snapshot, thành tiền do DB tính.
@Getter
@Setter
@NoArgsConstructor
@Entity
@DynamicUpdate
@Table(name = "quotation_line")
public class QuotationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", length = 30, nullable = false)
    private QuotationLineType lineType;

    // NULL chỉ với dòng điều chỉnh (CHECK ở DB)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "billable_service_id")
    private BillableService billableService;

    // Đơn giá lấy từ dòng giá nào. NULL với dòng điều chỉnh tay
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "price_item_id")
    private PriceItem priceItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "booking_horse_id")
    private BookingHorse bookingHorse;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "horse_leg_id")
    private HorseLeg horseLeg;

    @Column(name = "description", length = 500, nullable = false)
    private String description;

    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit", length = 30, nullable = false)
    private String unit;

    // snapshot: đơn giá tại thời điểm lập báo giá, không đổi theo bảng giá
    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    // Cột generated của DB: round(quantity * unit_price, 2)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "amount", precision = 15, scale = 2, insertable = false, updatable = false)
    private BigDecimal amount;

    // snapshot: dòng thu hộ hay không, chép từ dịch vụ lúc lập báo giá
    @Column(name = "is_pass_through", nullable = false)
    private Boolean passThrough;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_payment_id")
    private Payment refundPayment;

    @Column(name = "pickup_deposit_floor", nullable = false)
    private boolean pickupDepositFloor;
}
