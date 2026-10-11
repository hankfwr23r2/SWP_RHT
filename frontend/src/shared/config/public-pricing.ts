// Đơn giá cước xe theo bậc km (số mẫu), dùng cho báo giá thật. Biên lợi nhuận gộp vào đơn giá, không hiện riêng (booking-rules MARGIN_RATE).

export const TRIP_OPEN_FEE = 3_000_000 // phí mở chuyến, mỗi xe
export const KM_TIERS: [number, number][] = [[300, 26_000], [600, 20_000], [Infinity, 15_000]] // [đến km, ₫/km], mỗi xe 2 ngăn
export const BIG_TRUCK_FACTOR = 1.4 // xe 4 ngăn

// Ước tính quãng đường và thời gian
export const ROAD_FACTOR = 1.35 // đường bộ dài hơn đường chim bay
export const AVG_SPEED_KMH = 50
export const BORDER_HOURS = 3
export const DRIVE_HOURS_PER_DAY = 10
