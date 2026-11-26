-- Sinh trước chuyến từ lịch chạy 6 tuần thay cho 8 tuần (người dùng duyệt 26/11/2026).
-- Không thấp hơn 6 tuần: đơn nhập cần chuẩn bị giấy 30 ngày nên phải còn chuyến để chọn.
-- Chuyến đã sinh trước đó không bị xóa.
UPDATE system_config SET config_value = '6' WHERE config_key = 'DEPARTURE_GENERATION_WEEKS' AND config_value = '8';
