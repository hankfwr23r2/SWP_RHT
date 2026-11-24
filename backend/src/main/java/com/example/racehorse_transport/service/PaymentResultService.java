package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.payment.VnpayIpnResponseDto;
import com.example.racehorse_transport.dto.payment.PaymentResultResponseDto;
import java.util.Map;

public interface PaymentResultService {
    VnpayIpnResponseDto receive(Map<String, String> parameters);

    PaymentResultResponseDto receiveReturn(Map<String, String> parameters);
}
