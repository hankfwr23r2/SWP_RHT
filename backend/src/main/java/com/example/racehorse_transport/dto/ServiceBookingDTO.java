package com.example.racehorse_transport.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import com.example.racehorse_transport.entity.ServiceBookingId;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ServiceBookingDTO implements Serializable {
    private ServiceBookingId id;
    private Integer bookingID;
    private Integer horseID;
    private Integer serviceID;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
