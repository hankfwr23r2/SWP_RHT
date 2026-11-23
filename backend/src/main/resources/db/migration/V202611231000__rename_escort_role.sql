-- Người dùng chốt giữ vai trò ESCORT là nhân viên hộ tống, không gọi là groom (docs/NGU_CANH_THIET_KE_LAI.md).
-- V15 đã merge nên không sửa trực tiếp; đổi tên hiển thị bằng migration mới. Mã vai trò giữ nguyên.
UPDATE role SET name = 'Nhân viên hộ tống' WHERE code = 'ESCORT';
