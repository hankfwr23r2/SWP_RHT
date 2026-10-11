package com.example.racehorse_transport.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HorseDocumentDTO implements Serializable {
    private Integer id;
    private Integer horseID;
    private String documentType;
    private LocalDate expiryDate;
    private String status;
    private Instant uploadDate;
}
