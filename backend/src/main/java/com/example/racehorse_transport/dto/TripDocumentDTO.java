package com.example.racehorse_transport.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TripDocumentDTO implements Serializable {
    private Integer id;
    private Integer bookingID;
    private Integer regulationID;
    private Integer staffID;
    private String documentType;
    private String documentNumber;
    private String fileUrl;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String status;
}
