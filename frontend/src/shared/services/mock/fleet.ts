// Đội xe và người đi theo chuyến (tài xế, hộ tống). Gốc: Fleet And Route/ops_data.js (vehicles, staff).
// Gộp thêm xe và người đã ghi trong đơn mẫu (review, trip.contacts) để mọi trang dùng chung một danh sách.
// Mỗi xe có một tài xế cố định: chọn xe là gán tài xế theo.

export interface Vehicle {
  id: string
  name: string
  type: string
  capacity: number // số ngăn
  plate: string
  vin?: string // số khung
  inspectionNo?: string // số giấy đăng kiểm
  transitPermit?: string // giấy phép liên vận CLV
}

// Xe không gắn với tài xế: tài xế do Điều phối chọn khi lập lộ trình. Bảng này chỉ để dựng dữ liệu mẫu (đơn mẫu đã có tài xế).
const SEED_DRIVER: Record<string, string> = {'VH-001': 'TX-01', 'VH-002': 'TX-02', 'VH-003': 'TX-03', 'VH-004': 'TX-04', 'VH-005': 'TX-06', 'VH-006': 'TX-05', 'VH-007': 'TX-07', 'VH-008': 'TX-08', 'VH-009': 'TX-09', 'VH-010': 'TX-10', 'VH-011': 'TX-11', 'VH-012': 'TX-12', 'VH-013': 'TX-13', 'VH-014': 'TX-14'}
export const seedDriverOf = (vehicleId: string) => SEED_DRIVER[vehicleId] ?? ''

export interface CrewMember { id: string; name: string; role: 'driver' | 'escort'; phone: string; note?: string; idNumber?: string; license?: string }

const rawVehicles = (): Vehicle[] => [
  { id: 'VH-001', name: 'Xe chuyên dụng', type: 'Xe chuyên dụng', capacity: 2, plate: '29H-12345' },
  { id: 'VH-002', name: 'Xe chuyên dụng', type: 'Xe chuyên dụng', capacity: 4, plate: '51C-98765' },
  { id: 'VH-003', name: 'Xe chuyên dụng', type: 'Xe chuyên dụng', capacity: 4, plate: '30A-55678' },
  { id: 'VH-004', name: 'Xe chuyên dụng', type: 'Xe chuyên dụng', capacity: 6, plate: '51D-11122' },
  { id: 'VH-005', name: 'Xe chuyên dụng', type: 'Xe chuyên dụng', capacity: 2, plate: '43C-222.11' },
  { id: 'VH-006', name: 'Xe chuyên dụng', type: 'Xe chuyên dụng', capacity: 2, plate: '65C-101.22' },
  { id: 'VH-007', name: 'Xe chuyên dụng', type: 'Xe chuyên dụng', capacity: 4, plate: '51C-123.45' },
  { id: 'VH-008', name: 'Xe chuyên dụng', type: 'Xe chuyên dụng', capacity: 2, plate: '60C-222.10' },
  { id: 'VH-009', name: 'Xe chuyên dụng', type: 'Xe chuyên dụng', capacity: 4, plate: '29H-456.78' },
  { id: 'VH-010', name: 'Xe chuyên dụng', type: 'Xe chuyên dụng', capacity: 4, plate: '51C-888.99' },
  { id: 'VH-011', name: 'Xe chuyên dụng', type: 'Xe chuyên dụng', capacity: 2, plate: '61C-345.67' },
  { id: 'VH-012', name: 'Xe chuyên dụng', type: 'Xe chuyên dụng', capacity: 2, plate: '70C-045.18' },
  { id: 'VH-013', name: 'Xe chuyên dụng', type: 'Xe chuyên dụng', capacity: 2, plate: '29C-310.77' },
  { id: 'VH-014', name: 'Xe chuyên dụng', type: 'Xe chuyên dụng', capacity: 9, plate: '51D-909.09' },
]

const rawCrew = (): CrewMember[] => [
  { id: 'TX-01', name: 'Nguyễn Văn A', role: 'driver', phone: '0901 111 222' },
  { id: 'TX-02', name: 'Trần Văn B', role: 'driver', phone: '0901 333 444' },
  { id: 'TX-03', name: 'Lê Văn C', role: 'driver', phone: '0901 555 666' },
  { id: 'TX-04', name: 'Phạm Văn D', role: 'driver', phone: '0901 777 888' },
  { id: 'TX-05', name: 'Đặng Hoài Phúc', role: 'driver', phone: '0901 999 000' },
  { id: 'TX-06', name: 'Lê Minh Tuấn', role: 'driver', phone: '0903 121 314' },
  { id: 'TX-07', name: 'Nguyễn Văn Hùng', role: 'driver', phone: '0908 111 222' },
  { id: 'TX-08', name: 'Phan Thanh Hải', role: 'driver', phone: '0903 515 717' },
  { id: 'TX-09', name: 'Trần Quốc Bảo', role: 'driver', phone: '0903 818 919' },
  { id: 'TX-10', name: 'Phạm Đức Anh', role: 'driver', phone: '0904 202 303' },
  { id: 'TX-11', name: 'Võ Thanh Sơn', role: 'driver', phone: '0904 404 505' },
  { id: 'TX-12', name: 'Lê Văn Tài', role: 'driver', phone: '0904 606 707' },
  { id: 'TX-13', name: 'Trịnh Văn Long', role: 'driver', phone: '0904 808 909' },
  { id: 'TX-14', name: 'Hoàng Văn Tâm', role: 'driver', phone: '0905 121 212' },
  { id: 'TX-15', name: 'Bùi Quang Vinh', role: 'driver', phone: '0905 232 323' },
  { id: 'TX-16', name: 'Đinh Công Minh', role: 'driver', phone: '0905 343 434' },
  { id: 'TX-17', name: 'Mai Văn Khoa', role: 'driver', phone: '0905 454 545' },
  { id: 'TX-18', name: 'Cao Xuân Lộc', role: 'driver', phone: '0905 565 656' },
  { id: 'NV-01', name: 'Lê Thị C', role: 'escort', phone: '0902 111 222', note: 'NVCS 5 năm KN' },
  { id: 'NV-02', name: 'Võ Thị Lan', role: 'escort', phone: '0908 333 444', note: 'NVCS 3 năm KN' },
  { id: 'NV-03', name: 'Huỳnh Thị Mai', role: 'escort', phone: '0902 555 666', note: 'NVCS 2 năm KN' },
  { id: 'NV-04', name: 'Đỗ Văn Nam', role: 'escort', phone: '0902 777 888', note: 'NVCS 4 năm KN' },
  { id: 'NV-05', name: 'Đỗ Thị Hạnh', role: 'escort', phone: '0902 999 000', note: 'NVCS 1 năm KN' },
  { id: 'NV-06', name: 'Lý Thu Hà', role: 'escort', phone: '0906 121 212', note: 'NVCS 3 năm KN' },
  { id: 'NV-07', name: 'Ngô Thanh Tâm', role: 'escort', phone: '0906 232 323', note: 'NVCS 2 năm KN' },
  { id: 'NV-08', name: 'Dương Mỹ Linh', role: 'escort', phone: '0906 343 434', note: 'NVCS 4 năm KN' },
  { id: 'NV-09', name: 'Tạ Hoàng Yến', role: 'escort', phone: '0906 454 545', note: 'NVCS 1 năm KN' },
  { id: 'NV-10', name: 'Phùng Bảo Ngọc', role: 'escort', phone: '0906 565 656', note: 'NVCS 5 năm KN' },
]

// Giấy tờ xe và định danh nhân sự là số mẫu sinh theo mã, để Carrier Info Sheet có đủ trường (PRD mục 2.7). Thay bằng dữ liệu thật khi có backend.
const num = (id: string) => id.replace(/\D/g, '').padStart(3, '0')
export const seedVehicles = (): Vehicle[] => rawVehicles().map(v => ({
  ...v,
  vin: `RHT${num(v.id)}EQ2026${String(Number(num(v.id)) * 7919).padStart(6, '0')}`,
  inspectionNo: `KD-2026-${num(v.id)}${v.plate.replace(/\D/g, '').slice(0, 3)}`,
  transitPermit: `CLV-26-${num(v.id)}`,
}))
export const seedCrew = (): CrewMember[] => rawCrew().map(c => {
  const n = Number(num(c.id)) + (c.role === 'escort' ? 500 : 0) // tài xế và hộ tống cùng số thứ tự vẫn có CCCD khác nhau
  return {
    ...c,
    idNumber: `0${String(n).padStart(3, '0')}2${String(n * 104729).padStart(7, '0').slice(0, 7)}`,
    license: c.role === 'driver' ? `FC-${num(c.id)}-${String(n * 3571).padStart(5, '0')}` : undefined,
  }
})
