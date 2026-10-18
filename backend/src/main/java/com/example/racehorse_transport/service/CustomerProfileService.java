package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.customer.CustomerProfileResponse;
import com.example.racehorse_transport.dto.customer.UpdateCustomerProfileRequest;

public interface CustomerProfileService {
    CustomerProfileResponse getProfile(String username);
    CustomerProfileResponse updateProfile(String username, UpdateCustomerProfileRequest request);
}
