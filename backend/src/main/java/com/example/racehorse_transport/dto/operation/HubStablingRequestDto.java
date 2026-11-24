package com.example.racehorse_transport.dto.operation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// Gửi chuồng khi người nhận tới hub trễ hơn giờ hẹn: phí gửi chuồng do người nhận chịu (cost_item STABLING, CONSIGNEE)
public record HubStablingRequestDto(@NotNull @Positive BigDecimal fee, @Size(max = 500) String note) {}
