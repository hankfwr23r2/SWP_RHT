package com.example.racehorse_transport.dto;

import java.math.BigDecimal;

public record HorseTypeDTO(Integer id, String typeName, String description, BigDecimal insuredValue) {
    public HorseTypeDTO(Integer id, String typeName, String description) {
        this(id, typeName, description, null);
    }
}
