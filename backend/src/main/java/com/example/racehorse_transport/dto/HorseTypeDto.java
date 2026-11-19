package com.example.racehorse_transport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(name = "HorseTypeDTO")
public record HorseTypeDto(Integer id, String typeName, String description, BigDecimal insuredValue) {
    public HorseTypeDto(Integer id, String typeName, String description) {
        this(id, typeName, description, null);
    }
}
