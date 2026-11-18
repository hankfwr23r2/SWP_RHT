package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.admin.AccountLogResponseDto;
import com.example.racehorse_transport.dto.admin.AccountResponseDto;
import com.example.racehorse_transport.dto.admin.CreateStaffRequestDto;

import java.util.List;

public interface AdminAccountService {

    // 1. Lấy toàn bộ danh sách tài khoản (Staff + Customer) cho Admin
    List<AccountResponseDto> getAllAccounts();

    // 2. Admin tạo tài khoản nhân viên mới
    AccountResponseDto createStaffAccount(CreateStaffRequestDto request, String actorUsername);
    AccountResponseDto createStaffAccount(CreateStaffRequestDto request);

    // Cập nhật thông tin tài khoản (Họ tên, Email, SĐT)
    AccountResponseDto updateAccount(Integer userId, com.example.racehorse_transport.dto.admin.UpdateAccountRequestDto request, String actorUsername);

    // 3. Khóa hoặc mở khóa tài khoản
    void toggleAccountStatus(Integer userId, boolean lock, String actorUsername, String reason);
    void toggleAccountStatus(Integer userId, boolean lock);

    // 4. Đặt lại mật khẩu tạm thời cho nhân viên
    String resetPassword(Integer userId, String actorUsername);
    String resetPassword(Integer userId);

    // 5. Lấy lịch sử audit log của 1 tài khoản cụ thể
    List<AccountLogResponseDto> getAccountLogs(Integer userId);

    // 6. Lấy toàn bộ lịch sử audit log tài khoản trong hệ thống
    List<AccountLogResponseDto> getAllAccountLogs();
}
