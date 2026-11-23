package com.example.racehorse_transport.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CategoryResponse")
public class CategoryResponseDto {
    private Integer id;
    private String categoryName;
    private String description;
}
