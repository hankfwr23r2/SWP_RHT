package com.example.racehorse_transport.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VnpayIpnResponseDto(@JsonProperty("RspCode") String code, @JsonProperty("Message") String message) {}
