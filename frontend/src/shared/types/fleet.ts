// Một đơn đã nhận xe nhưng chưa lăn bánh (do backend tính): chỉ để hiển thị lịch, không tự khóa xe cho ngày khác
export interface VehicleSchedule {
  bookingCode: string
  tripType: 'domestic' | 'international'
  departAt: number
  busyFrom: number // đầu khoảng giữ xe (gồm chuẩn bị)
  busyUntil: number // cuối khoảng giữ xe (gồm quay về trụ sở)
  onTrip?: boolean // xe đã rời trụ sở cho đơn này, chưa xác nhận về
  returnEta?: number // giờ về trụ sở dự kiến (đã giao, đang về)
  driverName?: string // kíp của chuyến dùng xe này; chưa chốt phương án thì chưa có
  escortName?: string
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
  schedule?: VehicleSchedule[] // mọi đơn đang giữ xe (kể cả đang chạy), theo thời gian; chỉ có ở danh sách đội xe
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
