package com.example.racehorse_transport.dto.operation;

import jakarta.validation.constraints.Size;

// Gửi chuồng khi người nhận tới hub trễ hơn giờ hẹn: phí gửi chuồng do người nhận chịu (cost_item STABLING, CONSIGNEE)
public record HubStablingRequestDto(@Size(max = 500) String note) {}
