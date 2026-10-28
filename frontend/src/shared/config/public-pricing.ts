// Đơn giá cước xe theo bậc km (số mẫu), dùng cho báo giá thật. Biên lợi nhuận gộp vào đơn giá, không hiện riêng (booking-rules MARGIN_RATE).

export let TRIP_OPEN_FEE = 3_000_000 // phí mở chuyến, mỗi xe
export let KM_TIERS: [number, number][] = [[300, 26_000], [600, 20_000], [Infinity, 15_000]] // [đến km, ₫/km], mỗi xe 2 ngăn
export let BIG_TRUCK_FACTOR = 1.4 // xe 4 ngăn

export function applyRuntimePublicPricingConfig(config: {
  tripOpenFee?: number
  kmTier1Limit?: number
  kmTier1Rate?: number
  kmTier2Limit?: number
  kmTier2Rate?: number
  kmTier3Rate?: number
  bigTruckFactor?: number
}) {
  if (config.tripOpenFee != null && Number.isFinite(config.tripOpenFee)) {
    TRIP_OPEN_FEE = config.tripOpenFee
  }
  if (config.bigTruckFactor != null && Number.isFinite(config.bigTruckFactor)) {
    BIG_TRUCK_FACTOR = config.bigTruckFactor
  }
  const t1Limit = config.kmTier1Limit != null && Number.isFinite(config.kmTier1Limit) ? config.kmTier1Limit : KM_TIERS[0][0]
  const t1Rate = config.kmTier1Rate != null && Number.isFinite(config.kmTier1Rate) ? config.kmTier1Rate : KM_TIERS[0][1]
  const t2Limit = config.kmTier2Limit != null && Number.isFinite(config.kmTier2Limit) ? config.kmTier2Limit : KM_TIERS[1][0]
  const t2Rate = config.kmTier2Rate != null && Number.isFinite(config.kmTier2Rate) ? config.kmTier2Rate : KM_TIERS[1][1]
  const t3Rate = config.kmTier3Rate != null && Number.isFinite(config.kmTier3Rate) ? config.kmTier3Rate : KM_TIERS[2][1]
  KM_TIERS = [
    [t1Limit, t1Rate],
    [t2Limit, t2Rate],
    [Infinity, t3Rate],
  ]
}

// Ước tính quãng đường và thời gian
export const ROAD_FACTOR = 1.35 // đường bộ dài hơn đường chim bay
export const AVG_SPEED_KMH = 50
export const BORDER_HOURS = 3
export const DRIVE_HOURS_PER_DAY = 10
