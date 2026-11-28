-- Tuyến trục chính được xuất phát từ hub hoặc từ cửa khẩu (A1). Đơn IMPORT đi tuyến định kỳ từ cửa khẩu về hub,
-- đối xứng với chiều xuất. Loại điểm (HUB hoặc BORDER_GATE) được kiểm ở RouteStopsPolicy vì cần so với bảng location.
ALTER TABLE route RENAME COLUMN origin_hub_id TO origin_location_id;
ALTER TABLE route RENAME CONSTRAINT fk_route_origin_hub_id TO fk_route_origin_location_id;
ALTER INDEX ix_route_origin_hub_id RENAME TO ix_route_origin_location_id;
