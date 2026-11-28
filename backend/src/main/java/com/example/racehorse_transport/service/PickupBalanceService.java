package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.operation.BalanceHoldContactRequestDto;
import com.example.racehorse_transport.dto.operation.PickupHubTransferRequestDto;
import com.example.racehorse_transport.dto.operation.PickupWaitExtensionRequestDto;

public interface PickupBalanceService {
    void recordContact(Long bookingId, BalanceHoldContactRequestDto request, String username);
    void extend(Long bookingId, PickupWaitExtensionRequestDto request, String username);
    void transferToHub(Long bookingId, PickupHubTransferRequestDto request, String username);
    boolean canLeaveForHub(Long bookingId);
}
