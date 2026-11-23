package com.example.racehorse_transport.dto.route;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RouteDTO")
public class RouteDto implements Serializable {
    private Integer id;
    private String routeName;
    private String description;
    private Integer estimatedTime;
    private BigDecimal distance;
    private String status;
    private Integer originId;
    private String originName;
    private Integer destinationId;
    private String destinationName;
}
