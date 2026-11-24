package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.account.StaffAccountCreateRequestDto;
import com.example.racehorse_transport.dto.account.StaffAccountResponseDto;
import com.example.racehorse_transport.dto.account.StaffAccountStatusRequestDto;
import com.example.racehorse_transport.dto.account.StaffAccountUpdateRequestDto;
import java.util.List;

public interface AccountService {

    List<StaffAccountResponseDto> staffAccounts();

    StaffAccountResponseDto staffAccount(Long id);

    StaffAccountResponseDto createStaffAccount(StaffAccountCreateRequestDto request);

    StaffAccountResponseDto updateStaffAccount(Long id, StaffAccountUpdateRequestDto request);

    StaffAccountResponseDto lockStaffAccount(Long id, String username, StaffAccountStatusRequestDto request);

    StaffAccountResponseDto unlockStaffAccount(Long id, String username, StaffAccountStatusRequestDto request);
}
