package com.example.racehorse_transport.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import java.time.Instant;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionLogDTO implements Serializable {
    private Integer id;
    private Integer paymentID;
    private BigDecimal amount;
    private Instant transactionDate;
    private String status;
}
