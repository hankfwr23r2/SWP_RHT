// Một đơn đã nhận xe nhưng chưa lăn bánh (do backend tính): chỉ để hiển thị lịch, không tự khóa xe cho ngày khác
export interface VehicleSchedule {
  bookingCode: string
  tripType: 'domestic' | 'international'
  departAt: number
  busyFrom: number // đầu khoảng giữ xe (gồm chuẩn bị)
  busyUntil: number // cuối khoảng giữ xe (gồm quay về trụ sở)
}

// Rảnh/bận do backend quyết định, cùng quy tắc với bước lưu. Khi tải kèm ngày đi của một đơn:
// available = chọn được cho đơn đó. Khi tải không kèm ngày (trang Đội xe): available = sẵn sàng hẳn.
export interface Availability {
  available?: boolean
  busyReason?: string
  busyBookingCode?: string
  busyUntil?: number
}

export interface Vehicle extends Availability {
  id: string
  name: string
  type: string
  capacity: number // số ngăn
  plate: string
  status?: string // trạng thái vật lý người dùng đặt: AVAILABLE, MAINTENANCE
  onTrip?: boolean // đang lăn bánh hoặc chưa xác nhận về trụ sở
  onTripBookingCode?: string
  returnEta?: number
  upcoming?: VehicleSchedule[] // đơn đã nhận, chưa lăn bánh
}

export interface CrewMember extends Availability {
  id: string
  name: string
  role: 'driver' | 'escort'
  phone: string
  note?: string
  idNumber?: string
  license?: string
}
