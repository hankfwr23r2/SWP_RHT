package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.admin.AccountLogResponse;
import com.example.racehorse_transport.dto.admin.AccountResponse;
import com.example.racehorse_transport.dto.admin.CreateStaffRequest;

import java.util.List;

public interface AdminAccountService {

    // 1. Lấy toàn bộ danh sách tài khoản (Staff + Customer) cho Admin
    List<AccountResponse> getAllAccounts();

    // 2. Admin tạo tài khoản nhân viên mới
    AccountResponse createStaffAccount(CreateStaffRequest request, String actorUsername);
    AccountResponse createStaffAccount(CreateStaffRequest request);

    // 3. Khóa hoặc mở khóa tài khoản
    void toggleAccountStatus(Integer userId, boolean lock, String actorUsername, String reason);
    void toggleAccountStatus(Integer userId, boolean lock);

    // 4. Đặt lại mật khẩu tạm thời cho nhân viên
    String resetPassword(Integer userId, String actorUsername);
    String resetPassword(Integer userId);

    // 5. Lấy lịch sử audit log của 1 tài khoản cụ thể
    List<AccountLogResponse> getAccountLogs(Integer userId);

    // 6. Lấy toàn bộ lịch sử audit log tài khoản trong hệ thống
    List<AccountLogResponse> getAllAccountLogs();
}
