package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.pricing.PricingPreviewRequestDto;
import com.example.racehorse_transport.dto.pricing.PricingPreviewResponseDto;
import com.example.racehorse_transport.dto.pricing.TransferOptionsRequestDto;
import com.example.racehorse_transport.dto.pricing.TransferOptionsResponseDto;
import com.example.racehorse_transport.policy.PricingPolicy.PricingInput;
import com.example.racehorse_transport.policy.PricingPolicy.PricingResult;

public interface PricingService {
    PricingPreviewResponseDto preview(PricingPreviewRequestDto request, String username);

    // Công ty có chở được đầu đón, đầu giao hay không, kiểm ngay ở bước chọn chuyến
    TransferOptionsResponseDto transferOptions(TransferOptionsRequestDto request, String username);

    PricingResult calculate(PricingInput input);
}
